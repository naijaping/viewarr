package io.github.petebutty.viewarr.data.sync

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.petebutty.viewarr.data.local.LocalWatch
import io.github.petebutty.viewarr.data.local.LocalWatchStore
import io.github.petebutty.viewarr.data.remote.services.HttpJson
import io.github.petebutty.viewarr.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IPTV (Xtream VOD through Dispatcharr) and addon watch progress shared between every box signed in to the same
 * Dispatcharr user, through Dispatcharr's own per-user app-data store (as the setup is), key [KEY]. The format is
 * shared with companion plugins (ullms's "VOD Watch Progress Companion" syncs it with Emby / Jellyfin):
 *
 *   { "tmdb_movie_693134": {"title": "Dune: Part Two", "pos": 2415, "dur": 9960, "status": "watching", "updated_at": 1794782733},
 *     "tmdb_tv_94997_s02_e04": {"status": "watched", "updated_at": 1794782340},
 *     "tmdb_movie_550": {"status": "unwatched", "updated_at": 1794782900} }
 *
 * Ids: tmdb_movie_<id> / tmdb_tv_<show id>_s<SS>_e<EE>, else imdb_…, else title_movie_<slug> / title_tv_<slug>_s…_e…;
 * pos / dur and updated_at in seconds. Per item the newer updated_at wins (5 s leeway between clocks). Each sync reads
 * the copy, merges, then writes (the whole value is replaced each time, so writing blind lost another box's save).
 * "unwatched" markers tell other boxes to clear their tick (kept 90 days); watched items carry no position. Dispatcharr
 * allows 128 KiB per key: kept under 115 KiB, oldest watched dropped first. Viewarr adds "key", "type", "poster" to
 * items being watched (so another box can show them in Continue watching); others ignore them.
 * Synced on start, every 5 minutes, and a minute after a change here.
 */
@Singleton
class WatchSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionStore,
    private val store: LocalWatchStore,
    private val http: HttpJson,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val prefs by lazy { context.getSharedPreferences("watch_sync", Context.MODE_PRIVATE) }
    @Volatile private var dirty = false
    private var started = false

    fun start() {
        if (started) return
        started = true
        // A change here (played, ticked, un-ticked): synced within a minute.
        scope.launch { store.entries.drop(1).collect { dirty = true } }
        scope.launch {
            delay(5_000)
            var lastFull = 0L
            while (true) {
                val now = System.currentTimeMillis()
                if (dirty || now - lastFull > 5 * 60_000L) {
                    dirty = false
                    lastFull = now
                    runCatching { sync() }.onFailure { Log.w(TAG, "sync failed: ${it.message}") }
                }
                delay(60_000)
            }
        }
    }

    /** One sync: read the shared copy, merge with this box, write it back if it changed, and take in what's newer. */
    suspend fun sync() = lock.withLock {
        val s = sessions.session.first()?.takeIf { !it.isDirect } ?: return@withLock
        val url = s.serverUrl.trimEnd('/') + "/api/accounts/users/me/app-data/$KEY/"
        val headers = mapOf("X-API-Key" to s.apiKey)
        val r = http.get(url, headers)
        val remote: Map<String, JsonObject> = when {
            r.code in 200..299 -> (r.body as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to it } }?.toMap() ?: return@withLock
            r.code == 404 -> emptyMap()
            else -> { Log.w(TAG, "couldn't read the shared copy (${r.code})"); return@withLock }
        }
        val local = store.entries.value
        val localByKey = local.groupBy { keyOf(it) }.mapValues { (_, v) -> v.maxBy { it.changed } }
        val snapKey = "seen|" + s.serverUrl.trimEnd('/') + "|" + s.username
        val seenBefore = prefs.getStringSet(snapKey, emptySet()).orEmpty()
        val nowSec = System.currentTimeMillis() / 1000
        val merged = LinkedHashMap(remote)
        var changedRemote = false
        // This box → the shared copy (newer here, or not there yet).
        for ((k, e) in localByKey) {
            val mine = (e.changed / 1000)
            val theirs = remote[k]?.updatedAt() ?: -1
            if (mine > theirs + LEEWAY_S) { merged[k] = toRemote(e, mine); changedRemote = true }
        }
        // Un-ticked here since the last sync (the entry went): an "unwatched" marker for the other boxes.
        for (k in seenBefore) {
            if (k in localByKey) continue
            val theirs = remote[k]
            if (theirs != null && theirs.status() != "unwatched" && nowSec > theirs.updatedAt() + LEEWAY_S) {
                merged[k] = buildJsonObject { put("status", "unwatched"); put("updated_at", nowSec) }
                changedRemote = true
            }
        }
        // Old markers out; then under the quota.
        val cut = nowSec - UNWATCHED_KEEP_DAYS * 86_400L
        merged.entries.removeAll { (_, v) -> v.status() == "unwatched" && v.updatedAt() < cut }.also { if (it) changedRemote = true }
        while (merged.size > 1 && sizeOf(merged) > MAX_BYTES) {
            val pool = merged.filterValues { it.status() == "watched" }.keys.ifEmpty { merged.keys }
            merged.remove(pool.minBy { merged[it]?.updatedAt() ?: 0 }); changedRemote = true
        }
        if (changedRemote) {
            val w = http.put(url, JsonObject(merged), headers)
            if (!w.ok) { Log.w(TAG, "couldn't save the shared copy (${w.code})"); return@withLock }
        }
        // The shared copy → this box (newer there).
        var taken = 0
        store.syncEdit { list ->
            val out = list.toMutableList()
            for ((k, v) in merged) {
                val here = localByKey[k]
                if (here != null && here.changed / 1000 + LEEWAY_S >= v.updatedAt()) continue
                val ref = parseKey(k) ?: continue
                val at = v.updatedAt() * 1000
                val status = v.status()
                val pos = (v.long("pos") ?: 0) * 1000
                // A "watching" item with no position: nothing to take in. Checked before this box's entry is removed
                // (removing it first lost it here, and the next sync then sent the other boxes an "unwatched" marker).
                if (status != "unwatched" && status != "watched" && pos <= 0) continue
                out.removeAll { keyOf(it) == k }
                when (status) {
                    "unwatched" -> Unit
                    "watched" -> out += base(here, ref, v).copy(watched = true, positionMs = 0, changedAt = at, updatedAt = here?.updatedAt ?: at)
                    else -> out += base(here, ref, v).copy(watched = false, positionMs = pos, durationMs = (v.long("dur") ?: 0) * 1000,
                        changedAt = at, updatedAt = at, hidden = false)
                }
                taken++
            }
            out
        }
        prefs.edit().putStringSet(snapKey, store.entries.value.map { keyOf(it) }.toSet()).apply()
        if (changedRemote || taken > 0) Log.i(TAG, "synced: ${merged.size} on Dispatcharr${if (changedRemote) ", saved" else ""}${if (taken > 0) ", $taken taken in" else ""}")
    }

    private fun JsonObject.updatedAt(): Long = (this["updated_at"] as? JsonPrimitive)?.longOrNull ?: 0
    private fun JsonObject.status(): String = (this["status"] as? JsonPrimitive)?.content ?: "watching"
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
    private fun sizeOf(m: Map<String, JsonObject>) = JsonObject(m).toString().toByteArray().size

    /** A local entry as the shared format. */
    private fun toRemote(e: LocalWatch, atSec: Long): JsonObject = buildJsonObject {
        if (e.watched) { put("status", "watched"); put("updated_at", atSec); return@buildJsonObject }
        put("title", if (e.season != null) "${e.title} S%02dE%02d".format(java.util.Locale.ROOT, e.season, e.episode ?: 0) else e.title)
        put("pos", e.positionMs / 1000)
        put("dur", e.durationMs / 1000)
        put("status", "watching")
        put("updated_at", atSec)
        put("key", e.key)
        put("type", e.type)
        e.poster?.let { put("poster", it) }
    }

    /** What a shared id refers to. */
    private data class Ref(val type: String, val tmdb: Int?, val imdb: String?, val slug: String?, val season: Int?, val episode: Int?)

    private fun parseKey(k: String): Ref? {
        TV.matchEntire(k)?.let { m ->
            val (src, id, se, ep) = m.destructured
            return Ref("Show", id.toIntOrNull().takeIf { src == "tmdb" }, id.takeIf { src == "imdb" }, id.takeIf { src == "title" }, se.toInt(), ep.toInt())
        }
        MOVIE.matchEntire(k)?.let { m ->
            val (src, id) = m.destructured
            return Ref("Movie", id.toIntOrNull().takeIf { src == "tmdb" }, id.takeIf { src == "imdb" }, id.takeIf { src == "title" }, null, null)
        }
        return null
    }

    /** The entry to update, or a new one for a title this box hasn't seen (another box, or Emby, watched it). */
    private fun base(here: LocalWatch?, ref: Ref, v: JsonObject): LocalWatch {
        if (here != null) return here
        val title = v.text("title")?.replace(Regex("\\s+S\\d+E\\d+$"), "") ?: ref.slug?.replace('_', ' ') ?: "Unknown"
        val id = when { ref.tmdb != null -> "tmdb:${ref.type}:${ref.tmdb}"; ref.imdb != null -> "imdb:${ref.imdb}"; else -> "title:${ref.slug}" }
        return LocalWatch(
            id = id, type = v.text("type") ?: ref.type, title = title, poster = v.text("poster"),
            tmdbId = ref.tmdb, imdbId = ref.imdb, key = v.text("key") ?: id, season = ref.season, episode = ref.episode,
        )
    }

    companion object {
        const val KEY = "viewarr_vod_progress"
        private const val TAG = "WatchSync"
        private const val LEEWAY_S = 5L
        private const val UNWATCHED_KEEP_DAYS = 90
        private const val MAX_BYTES = 115_000
        private val TV = Regex("(tmdb|imdb|title)_tv_(.+)_s(\\d+)_e(\\d+)")
        private val MOVIE = Regex("(tmdb|imdb|title)_movie_(.+)")

        /** A local entry's shared id (TMDB first, then IMDb, then its title). Digits always plain 0–9 (Locale.ROOT): on an
         *  Arabic or Persian box "%02d" wrote other digits, and its ids matched no other box or companion plugin. */
        fun keyOf(e: LocalWatch): String {
            val se = if (e.season != null) "_s%02d_e%02d".format(java.util.Locale.ROOT, e.season, e.episode ?: 0) else ""
            val kind = if (e.season != null || e.type == "Show") "tv" else "movie"
            return when {
                e.tmdbId != null -> "tmdb_${kind}_${e.tmdbId}$se"
                e.imdbId != null -> "imdb_${kind}_${e.imdbId}$se"
                else -> "title_${kind}_${slug(e.title)}$se"
            }
        }

        private fun slug(t: String) = t.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').replace(Regex("_+"), "_")
    }
}
