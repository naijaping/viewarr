package io.github.petebutty.viewarr.data.m3u

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class M3uException(message: String) : IOException(message)

/** One live channel from an M3U playlist. */
data class M3uChannel(
    val name: String,
    val url: String,
    val group: String?,
    val logo: String?,
    /** tvg-id: the channel's id in the XMLTV guide. */
    val tvgId: String?,
    val number: Double?,
    /** tvg-shift: the guide's hours out for this channel (as Kodi and Emby read it), in minutes; 0 when none. */
    val shiftMin: Int = 0,
)

data class M3uPlaylist(val channels: List<M3uChannel>, /** The guide link in the playlist's header (url-tvg / x-tvg-url). */ val guideUrl: String?)

/**
 * Plain M3U playlists (what many IPTV providers hand out instead of an Xtream Codes login): #EXTINF lines with
 * tvg-id, tvg-name, tvg-logo, group-title and tvg-chno, each followed by its stream link. Films and series in the
 * playlist are left out (live channels only). Read as it downloads: big playlists run to tens of MB.
 */
@Singleton
class M3uClient @Inject constructor(private val client: OkHttpClient) {

    suspend fun load(url: String): M3uPlaylist = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", "Viewarr").build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw M3uException("The provider refused that playlist link (error ${r.code}).")
            if (!r.isSuccessful) throw M3uException("The playlist didn't load (error ${r.code}).")
            val reader = r.body!!.byteStream().bufferedReader(Charsets.UTF_8)
            val channels = ArrayList<M3uChannel>()
            var guide: String? = null
            var info: String? = null
            var group: String? = null
            var sawHeader = false
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTM3U") -> {
                        sawHeader = true
                        guide = attr(line, "url-tvg") ?: attr(line, "x-tvg-url")
                        guide = guide?.split(',')?.firstOrNull()?.trim()?.takeIf { it.startsWith("http") }
                    }
                    line.startsWith("#EXTINF") -> { sawHeader = true; info = line; group = null }
                    line.startsWith("#EXTGRP:") -> group = line.removePrefix("#EXTGRP:").trim()
                    line.startsWith("#") -> Unit
                    else -> {
                        val i = info
                        info = null
                        if (i == null || !isLive(line)) return@forEach
                        val name = i.substringAfterLast(',').trim().ifEmpty { attr(i, "tvg-name") ?: "Channel ${channels.size + 1}" }
                        channels += M3uChannel(
                            name = name,
                            url = line,
                            group = attr(i, "group-title") ?: group,
                            logo = attr(i, "tvg-logo")?.takeIf { it.startsWith("http") },
                            tvgId = attr(i, "tvg-id")?.takeIf { it.isNotBlank() },
                            number = (attr(i, "tvg-chno") ?: attr(i, "channel-number"))?.toDoubleOrNull(),
                            shiftMin = attr(i, "tvg-shift")?.trim()?.replace(',', '.')?.toDoubleOrNull()?.let { (it * 60).toInt().coerceIn(-24 * 60, 24 * 60) } ?: 0,
                        )
                    }
                }
            }
            if (!sawHeader) throw M3uException("That link isn't an M3U playlist.")
            if (channels.isEmpty()) throw M3uException("The playlist has no live channels.")
            M3uPlaylist(channels, guide)
        }
    }

    /** One pattern per attribute name, made once: a playlist can have tens of thousands of lines, each asked for 7. */
    private val attrPatterns = ConcurrentHashMap<String, Regex>()

    private fun attr(line: String, name: String): String? =
        attrPatterns.getOrPut(name) { Regex("""\b$name="([^"]*)"""") }.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /** Live channels only: provider playlists often list their films and series too. */
    private fun isLive(url: String): Boolean {
        val u = url.lowercase().substringBefore('?')
        if ("/movie/" in u || "/series/" in u) return false
        return !(u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi"))
    }
}
