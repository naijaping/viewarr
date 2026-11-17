package io.github.petebutty.viewarr.data.xtream

import android.util.Xml
import io.github.petebutty.viewarr.data.local.Packed
import io.github.petebutty.viewarr.data.local.ProgramEntity
import io.github.petebutty.viewarr.data.session.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What a provider says about the account when signing in. */
data class XtreamAccount(val username: String, val timezone: String?, val expires: Long?, val maxConnections: Int?)

class XtreamException(message: String) : IOException(message)

data class XtreamCategory(val id: String, val name: String)
data class XtreamChannel(
    val streamId: Int,
    val name: String,
    val number: Double?,
    val logo: String?,
    val epgId: String?,
    val categoryId: String?,
    val archiveDays: Int,
)
data class XtreamVod(
    val streamId: Int,
    val name: String,
    val poster: String?,
    val rating: Double?,
    val year: Int?,
    val tmdbId: Int?,
    val extension: String,
    val added: Long,
    val categoryId: String?,
)
data class XtreamSeries(
    val seriesId: Int,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val plot: String?,
    val rating: Double?,
    val year: Int?,
    val tmdbId: Int?,
    val lastModified: Long,
    val categoryId: String?,
)
data class XtreamEpisode(val id: String, val season: Int, val episode: Int, val title: String, val extension: String)

/**
 * One list item read on its own: an item that isn't what's expected (a null, a string, a broken object: some
 * providers send them) comes out as null and is skipped, rather than emptying the whole list.
 */
private class Tolerant<T : Any>(private val inner: KSerializer<T>) : KSerializer<T?> {
    override val descriptor = inner.descriptor
    override fun deserialize(decoder: Decoder): T? {
        val d = decoder as? JsonDecoder ?: return null
        val el = d.decodeJsonElement()
        return runCatching { d.json.decodeFromJsonElement(inner, el) }.getOrNull()
    }
    override fun serialize(encoder: Encoder, value: T?) = throw UnsupportedOperationException()
}

// What's kept of each get_vod_streams / get_series item. Providers send the same field as a string, a number or
// null (and now and then something else), so each is any JSON value, read as text by [s].
@Serializable
private class VodDto(
    val name: JsonElement? = null,
    @SerialName("stream_id") val streamId: JsonElement? = null,
    @SerialName("stream_icon") val streamIcon: JsonElement? = null,
    val rating: JsonElement? = null,
    val year: JsonElement? = null,
    val tmdb: JsonElement? = null,
    @SerialName("tmdb_id") val tmdbId: JsonElement? = null,
    @SerialName("container_extension") val containerExtension: JsonElement? = null,
    val added: JsonElement? = null,
    @SerialName("category_id") val categoryId: JsonElement? = null,
)

@Serializable
private class SeriesDto(
    val name: JsonElement? = null,
    @SerialName("series_id") val seriesId: JsonElement? = null,
    val cover: JsonElement? = null,
    @SerialName("backdrop_path") val backdropPath: JsonElement? = null,
    val plot: JsonElement? = null,
    val rating: JsonElement? = null,
    val releaseDate: JsonElement? = null,
    @SerialName("release_date") val releaseDate2: JsonElement? = null,
    val tmdb: JsonElement? = null,
    @SerialName("tmdb_id") val tmdbId: JsonElement? = null,
    @SerialName("last_modified") val lastModified: JsonElement? = null,
    @SerialName("category_id") val categoryId: JsonElement? = null,
)

/** A JSON value as text (a string or number), as [XtreamClient.Companion.str] reads an object's field. */
private fun JsonElement?.s(): String? = (this as? JsonPrimitive)?.contentOrNull

/**
 * IPTV providers using the Xtream Codes API (player_api.php, xmltv.php and /live, /movie, /series, /timeshift
 * stream paths). Used when live TV is signed in to a provider rather than Dispatcharr.
 */
@Singleton
class XtreamClient @Inject constructor(private val client: OkHttpClient, private val json: Json) {

    /** Checks the login; the provider's own answer, not just "a server responded". */
    suspend fun signIn(server: String, username: String, password: String): XtreamAccount {
        val root = get(api(server, username, password, null)) as? JsonObject
            ?: throw XtreamException("That address didn't answer like an IPTV provider (Xtream Codes). Check it with your provider.")
        val user = root["user_info"] as? JsonObject
            ?: throw XtreamException("That address didn't answer like an IPTV provider (Xtream Codes). Check it with your provider.")
        if (user.str("auth") != "1") throw XtreamException("Your provider didn't accept that username and password. Use the ones from your provider's welcome email.")
        val status = user.str("status")
        val expires = user.str("exp_date")?.toLongOrNull()?.times(1000)
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw XtreamException("Your provider says this account is \"$status\"" +
                (expires?.let { " (it expired on ${DateFormat.getDateInstance().format(Date(it))})" } ?: "") + ".")
        }
        val tz = (root["server_info"] as? JsonObject)?.str("timezone")
        return XtreamAccount(user.str("username") ?: username, tz, expires, user.str("max_connections")?.toIntOrNull())
    }

    suspend fun liveCategories(s: Session) = categories(s, "get_live_categories")
    suspend fun vodCategories(s: Session) = categories(s, "get_vod_categories")
    suspend fun seriesCategories(s: Session) = categories(s, "get_series_categories")

    suspend fun liveStreams(s: Session): List<XtreamChannel> = list(s, "get_live_streams").mapNotNull { j ->
        val o = j as? JsonObject ?: return@mapNotNull null
        XtreamChannel(
            streamId = o.str("stream_id")?.toIntOrNull() ?: return@mapNotNull null,
            name = o.str("name").orEmpty(),
            number = o.str("num")?.toDoubleOrNull(),
            logo = o.str("stream_icon")?.takeIf { it.startsWith("http") },
            epgId = o.str("epg_channel_id")?.takeIf { it.isNotBlank() },
            categoryId = o.str("category_id"),
            archiveDays = if (o.str("tv_archive") == "1") o.str("tv_archive_duration")?.toIntOrNull() ?: 0 else 0,
        )
    }

    // The two big lists (20,000+ titles on big providers, tens of MB) are read item by item straight off the
    // connection, keeping only the fields below: not the whole reply as one String plus a JSON tree of it.

    suspend fun vodStreams(s: Session): List<XtreamVod> = stream(s, "get_vod_streams", ListSerializer(Tolerant(VodDto.serializer()))).filterNotNull().mapNotNull { o ->
        val name = o.name.s().orEmpty()
        XtreamVod(
            streamId = o.streamId.s()?.toIntOrNull() ?: return@mapNotNull null,
            name = cleanTitle(name),
            poster = o.streamIcon.s()?.takeIf { it.startsWith("http") },
            rating = o.rating.s()?.toDoubleOrNull()?.takeIf { it > 0 },
            year = o.year.s()?.take(4)?.toIntOrNull() ?: yearIn(name),
            tmdbId = o.tmdb.s()?.toIntOrNull() ?: o.tmdbId.s()?.toIntOrNull(),
            extension = o.containerExtension.s() ?: "mp4",
            added = o.added.s()?.toLongOrNull()?.times(1000) ?: 0,
            categoryId = o.categoryId.s(),
        )
    }

    suspend fun series(s: Session): List<XtreamSeries> = stream(s, "get_series", ListSerializer(Tolerant(SeriesDto.serializer()))).filterNotNull().mapNotNull { o ->
        val name = o.name.s().orEmpty()
        XtreamSeries(
            seriesId = o.seriesId.s()?.toIntOrNull() ?: return@mapNotNull null,
            name = cleanTitle(name),
            poster = o.cover.s()?.takeIf { it.startsWith("http") },
            backdrop = (o.backdropPath as? JsonArray)?.firstOrNull()?.s()?.takeIf { it.startsWith("http") },
            plot = o.plot.s()?.takeIf { it.isNotBlank() },
            rating = o.rating.s()?.toDoubleOrNull()?.takeIf { it > 0 },
            year = (o.releaseDate.s() ?: o.releaseDate2.s())?.take(4)?.toIntOrNull() ?: yearIn(name),
            tmdbId = o.tmdb.s()?.toIntOrNull() ?: o.tmdbId.s()?.toIntOrNull(),
            lastModified = o.lastModified.s()?.toLongOrNull()?.times(1000) ?: 0,
            categoryId = o.categoryId.s(),
        )
    }

    /** Episodes of one series, by season. */
    suspend fun episodes(s: Session, seriesId: Int): List<XtreamEpisode> {
        val root = get(api(s, "get_series_info") + "&series_id=$seriesId") as? JsonObject ?: return emptyList()
        val eps = root["episodes"] as? JsonObject ?: return emptyList()
        return eps.entries.flatMap { (season, arr) ->
            (arr as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                XtreamEpisode(
                    id = o.str("id") ?: return@mapNotNull null,
                    season = o.str("season")?.toIntOrNull() ?: season.toIntOrNull() ?: 1,
                    episode = o.str("episode_num")?.toIntOrNull() ?: 0,
                    title = o.str("title").orEmpty(),
                    extension = o.str("container_extension") ?: "mp4",
                )
            }
        }
    }

    // ---- Stream addresses

    fun liveUrl(s: Session, streamId: Int) = "${base(s)}/live/${login(s)}/$streamId.ts"
    fun movieUrl(s: Session, streamId: Int, ext: String) = "${base(s)}/movie/${login(s)}/$streamId.$ext"
    fun episodeUrl(s: Session, e: XtreamEpisode) = "${base(s)}/series/${login(s)}/${e.id}.${e.extension}"

    /** Catch-up: the start time is written in the provider's own time zone. */
    fun catchupUrl(s: Session, streamId: Int, startMs: Long, minutes: Long): String {
        val zone = runCatching { ZoneId.of(s.xcTimezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), zone).format(TIMESHIFT)
        return "${base(s)}/timeshift/${login(s)}/$minutes/$start/$streamId.ts"
    }

    // ---- Guide (XMLTV)

    /**
     * Streams the provider's XMLTV guide, keeping only programmes for [wanted] channel ids that overlap [from]..[to].
     * The file can be tens of MB, so it's read as it downloads and handed over in batches.
     */
    suspend fun guide(s: Session, wanted: Set<String>, from: Long, to: Long, onBatch: suspend (List<ProgramEntity>) -> Unit) = withContext(Dispatchers.IO) {
        val url = "${base(s)}/xmltv.php?username=${enc(s.username)}&password=${enc(s.xcPassword.orEmpty())}"
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (!r.isSuccessful) throw XtreamException("The provider's TV guide didn't load (error ${r.code}).")
            parseXmltv(r.body!!.byteStream(), wanted, from, to, onBatch)
        }
    }

    /** Any XMLTV guide by its link (M3U playlists' guides); gzipped files (.xml.gz) are unpacked as they download. */
    suspend fun guideFromUrl(url: String, wanted: Set<String>, from: Long, to: Long, onBatch: suspend (List<ProgramEntity>) -> Unit) = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (!r.isSuccessful) throw XtreamException("The TV guide didn't load (error ${r.code}).")
            val raw = BufferedInputStream(r.body!!.byteStream())
            raw.mark(2)
            val gz = raw.read() == 0x1f && raw.read() == 0x8b
            raw.reset()
            parseXmltv(if (gz) GZIPInputStream(raw) else raw, wanted, from, to, onBatch)
        }
    }

    private suspend fun parseXmltv(stream: InputStream, wanted: Set<String>, from: Long, to: Long, onBatch: suspend (List<ProgramEntity>) -> Unit) {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(stream, null)
        val batch = ArrayList<ProgramEntity>(BATCH)
        var channel: String? = null
        var start = 0L
        var end = 0L
        var title: String? = null
        var sub: String? = null
        var desc: String? = null
        var icon: String? = null
        var season: Int? = null
        var episode: Int? = null
        var field: String? = null
        var keep = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "programme" -> {
                        channel = p.getAttributeValue(null, "channel")
                        start = parseXmltvTime(p.getAttributeValue(null, "start")) ?: 0
                        end = parseXmltvTime(p.getAttributeValue(null, "stop")) ?: 0
                        keep = channel != null && channel in wanted && end > from && start < to && end > start
                        title = null; sub = null; desc = null; season = null; episode = null; icon = null
                    }
                    "icon" -> if (keep && icon == null) icon = p.getAttributeValue(null, "src")?.takeIf { it.startsWith("http") }
                    "title", "sub-title", "desc" -> field = if (keep) p.name else null
                    "episode-num" -> field = if (keep && p.getAttributeValue(null, "system") == "xmltv_ns") "ep" else null
                }
                XmlPullParser.TEXT -> when (field) {
                    "title" -> title = (title.orEmpty() + p.text)
                    "sub-title" -> sub = (sub.orEmpty() + p.text)
                    "desc" -> desc = (desc.orEmpty() + p.text)
                    "ep" -> {
                        // xmltv_ns: "season.episode.part", zero-based.
                        val parts = p.text.split('.')
                        season = parts.getOrNull(0)?.trim()?.substringBefore('/')?.toIntOrNull()?.plus(1)
                        episode = parts.getOrNull(1)?.trim()?.substringBefore('/')?.toIntOrNull()?.plus(1)
                    }
                }
                XmlPullParser.END_TAG -> {
                    field = null
                    if (p.name == "programme" && keep) {
                        batch += ProgramEntity(
                            tvgId = channel!!, title = title?.trim().orEmpty(), subTitle = sub?.trim()?.takeIf { it.isNotEmpty() },
                            description = Packed.of(desc?.trim()), startTime = start, endTime = end,
                            season = season, episode = episode, isNew = false, isLive = false, isPremiere = false, image = icon,
                        )
                        if (batch.size >= BATCH) { onBatch(batch.toList()); batch.clear() }
                    }
                }
            }
            ev = p.next()
        }
        if (batch.isNotEmpty()) onBatch(batch.toList())
    }

    // ---- plumbing

    private suspend fun categories(s: Session, action: String): List<XtreamCategory> = list(s, action).mapNotNull { j ->
        val o = j as? JsonObject ?: return@mapNotNull null
        XtreamCategory(o.str("category_id") ?: return@mapNotNull null, o.str("category_name").orEmpty())
    }

    private suspend fun list(s: Session, action: String): List<JsonElement> =
        (get(api(s, action)) as? JsonArray).orEmpty()

    private suspend fun get(url: String): JsonElement? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw XtreamException("Your provider didn't accept that username and password.")
            if (!r.isSuccessful) throw XtreamException("The provider answered with an error (${r.code}).")
            val text = r.body?.string().orEmpty()
            runCatching { json.parseToJsonElement(text) }.getOrNull()
        }
    }

    /**
     * A list action decoded as it downloads. Like [list], a reply that isn't a list of titles is an empty list; a
     * dropped connection still throws.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> stream(s: Session, action: String, des: DeserializationStrategy<List<T>>): List<T> = withContext(Dispatchers.IO) {
        val url = api(s, action)
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw XtreamException("Your provider didn't accept that username and password.")
            if (!r.isSuccessful) throw XtreamException("The provider answered with an error (${r.code}).")
            val body = r.body ?: return@use emptyList()
            // SerializationException (not JSON, or not a list of objects) is an IllegalArgumentException.
            try { json.decodeFromStream(des, body.byteStream()) } catch (_: IllegalArgumentException) { emptyList() }
        }
    }

    private fun api(server: String, user: String, pass: String, action: String?) =
        "${server.trimEnd('/')}/player_api.php?username=${enc(user)}&password=${enc(pass)}" + (action?.let { "&action=$it" } ?: "")

    private fun api(s: Session, action: String) = api(s.serverUrl, s.username, s.xcPassword.orEmpty(), action)

    private fun base(s: Session) = s.serverUrl.trimEnd('/')

    /** The login as stream addresses carry it ("user/password"). */
    private fun login(s: Session) = "${enc(s.username)}/${enc(s.xcPassword.orEmpty())}"

    companion object {
        const val BATCH = 1000
        const val UA = "Viewarr"
        val TIMESHIFT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
        val XMLTV: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
        fun parseXmltvTime(s: String?): Long? {
            if (s.isNullOrBlank()) return null
            val t = s.trim()
            return runCatching { OffsetDateTime.parse(if (t.length == 14) "$t +0000" else t, XMLTV).toInstant().toEpochMilli() }.getOrNull()
        }
        /** "Name (2021)" or "EN - Name" style provider titles, tidied for display. */
        fun cleanTitle(name: String): String {
            var t = name.trim()
                .replace(LANG_BARS, "")          // "|EN| Name"
                .replace(LANG_DASH, "")          // "EN - Name", "NF - Name"
            while (true) { val n = t.replace(END_TAG, "").replace(END_QUALITY, ""); if (n == t || n.isBlank()) break; t = n }
            return t.trim()
        }
        fun yearIn(name: String): Int? = YEAR.find(name)?.groupValues?.get(1)?.toIntOrNull()

        // cleanTitle's patterns, made once (it runs for every title of a 20,000-title catalogue).
        private val LANG_BARS = Regex("^\\|[A-Z]{2,5}\\|\\s*")
        private val LANG_DASH = Regex("^[A-Z]{2,3}\\s*[|:-]\\s*")
        // Trailing year and provider tags: "Name (2026) (KR) (KOREAN MULTI SUB)", "Name [4K]".
        private val END_TAG = Regex("\\s*[(\\[](\\d{4}|[A-Z0-9][A-Z0-9 +&/-]*)[)\\]]\\s*$")
        // …and bare quality words left at the end: "Name 4K", "Name UHD HDR" (they stopped TMDB finding it).
        private val END_QUALITY = Regex("\\s+(4K|UHD|FHD|HD|SD|HDR|HDR10|DV|HEVC|H265|2160p|1080p|720p)$", RegexOption.IGNORE_CASE)
        private val YEAR = Regex("\\((\\d{4})\\)")
    }
}
