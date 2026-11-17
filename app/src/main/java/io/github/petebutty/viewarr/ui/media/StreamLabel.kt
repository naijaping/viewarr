package io.github.petebutty.viewarr.ui.media

import io.github.petebutty.viewarr.data.media.StreamOption

/**
 * A short line for an addon stream in Play from: what matters to pick one, read from the addon's own (often long,
 * emoji-filled) name and description. "1080p · REMUX · AVC · TrueHD 7.1 · 24 GB", with where it comes from
 * ("Usenet ⚡", "Torrent") beside it. Falls back to the name's first line when nothing is recognised.
 */
object StreamLabel {
    // The patterns, compiled once (they were compiled again for every stream, on every redraw of Play from).
    private val RES_4K = Regex("\\b(2160p|4k|uhd)\\b", RegexOption.IGNORE_CASE)
    private val RES_1440 = Regex("\\b1440p\\b", RegexOption.IGNORE_CASE)
    private val RES_1080 = Regex("\\b1080p\\b", RegexOption.IGNORE_CASE)
    private val RES_720 = Regex("\\b720p\\b", RegexOption.IGNORE_CASE)
    private val RES_SD = Regex("\\b(480p|576p|sd)\\b", RegexOption.IGNORE_CASE)
    private val REMUX = Regex("remux", RegexOption.IGNORE_CASE)
    private val BLURAY = Regex("blu-?ray|bdrip|brrip", RegexOption.IGNORE_CASE)
    private val WEB_DL = Regex("web-?dl", RegexOption.IGNORE_CASE)
    private val WEB_RIP = Regex("web-?rip", RegexOption.IGNORE_CASE)
    private val WEB = Regex("\\bweb\\b", RegexOption.IGNORE_CASE)
    private val HDTV = Regex("hdtv", RegexOption.IGNORE_CASE)
    private val CAM = Regex("\\b(cam|telesync|\\bts\\b)", RegexOption.IGNORE_CASE)
    private val DV = Regex("\\b(dv|dovi|dolby ?vision)\\b", RegexOption.IGNORE_CASE)
    private val HDR10_PLUS = Regex("hdr10\\+|hdr10plus", RegexOption.IGNORE_CASE)
    private val HDR = Regex("\\bhdr(10)?\\b", RegexOption.IGNORE_CASE)
    private val HEVC = Regex("\\b(hevc|x265|h\\.?265)\\b", RegexOption.IGNORE_CASE)
    private val AVC = Regex("\\b(avc|x264|h\\.?264)\\b", RegexOption.IGNORE_CASE)
    private val AV1 = Regex("\\bav1\\b", RegexOption.IGNORE_CASE)
    private val ATMOS = Regex("atmos", RegexOption.IGNORE_CASE)
    private val TRUEHD = Regex("truehd", RegexOption.IGNORE_CASE)
    private val DTS_HD = Regex("dts-?hd|dts-?x", RegexOption.IGNORE_CASE)
    private val DTS = Regex("\\bdts\\b", RegexOption.IGNORE_CASE)
    private val DD_PLUS = Regex("dd\\+|ddp|e-?ac-?3", RegexOption.IGNORE_CASE)
    private val DD = Regex("\\b(dd|ac-?3)\\b", RegexOption.IGNORE_CASE)
    private val AAC = Regex("\\baac\\b", RegexOption.IGNORE_CASE)
    private val CHANNELS = Regex("\\b([257]\\.1)\\b")
    private val SIZE = Regex("(\\d+(?:[.,]\\d+)?)\\s?(GB|GiB|MB|MiB)\\b", RegexOption.IGNORE_CASE)
    private val USENET = Regex("nzb|usenet|easynews", RegexOption.IGNORE_CASE)
    private val TORRENT = Regex("torrent|\\b(rd|pm|tb|ad)\\b|real-?debrid|premiumize|torbox|alldebrid", RegexOption.IGNORE_CASE)

    fun summary(s: StreamOption): String {
        val t = s.name + "\n" + s.description
        fun has(r: Regex) = r.containsMatchIn(t)
        val res = when {
            has(RES_4K) -> "4K"
            has(RES_1440) -> "1440p"
            has(RES_1080) -> "1080p"
            has(RES_720) -> "720p"
            has(RES_SD) -> "SD"
            else -> null
        }
        val source = when {
            has(REMUX) -> "REMUX"
            has(BLURAY) -> "BluRay"
            has(WEB_DL) -> "WEB-DL"
            has(WEB_RIP) -> "WEBRip"
            has(WEB) -> "WEB"
            has(HDTV) -> "HDTV"
            has(CAM) -> "CAM"
            else -> null
        }
        val hdr = listOfNotNull(
            "DV".takeIf { has(DV) },
            when { has(HDR10_PLUS) -> "HDR10+"; has(HDR) -> "HDR"; else -> null },
        ).joinToString(" ").ifEmpty { null }
        val codec = when { has(HEVC) -> "HEVC"; has(AVC) -> "AVC"; has(AV1) -> "AV1"; else -> null }
        val audio = when {
            has(ATMOS) -> "Atmos"
            has(TRUEHD) -> "TrueHD"
            has(DTS_HD) -> "DTS-HD"
            has(DTS) -> "DTS"
            has(DD_PLUS) -> "DD+"
            has(DD) -> "DD"
            has(AAC) -> "AAC"
            else -> null
        }?.let { a -> CHANNELS.find(t)?.groupValues?.get(1)?.let { "$a $it" } ?: a }
        val size = SIZE.find(t)?.let { m ->
            m.groupValues[1].replace(',', '.') + " " + m.groupValues[2].uppercase().replace("IB", "B")
        }
        val parts = listOfNotNull(res, source, hdr, codec, audio, size)
        return if (parts.size >= 2) parts.joinToString(" · ")
        else s.name.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(48) ?: s.addon
    }

    /** Where it comes from, short: Usenet or Torrent (⚡ when the debrid service already has it), else the addon. */
    fun from(s: StreamOption): String {
        val t = s.name + "\n" + s.description
        val cached = t.contains('⚡')
        val kind = when {
            USENET.containsMatchIn(t) -> "Usenet"
            TORRENT.containsMatchIn(t) -> "Torrent"
            else -> s.addon
        }
        return kind + if (cached) " ⚡" else ""
    }
}
