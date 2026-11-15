package io.github.petebutty.viewarr.ui.media

import io.github.petebutty.viewarr.data.media.StreamOption

/**
 * A short line for an addon stream in Play from: what matters to pick one, read from the addon's own (often long,
 * emoji-filled) name and description. "1080p · REMUX · AVC · TrueHD 7.1 · 24 GB", with where it comes from
 * ("Usenet ⚡", "Torrent") beside it. Falls back to the name's first line when nothing is recognised.
 */
object StreamLabel {
    fun summary(s: StreamOption): String {
        val t = s.name + "\n" + s.description
        fun has(r: String) = Regex(r, RegexOption.IGNORE_CASE).containsMatchIn(t)
        val res = when {
            has("\\b(2160p|4k|uhd)\\b") -> "4K"
            has("\\b1440p\\b") -> "1440p"
            has("\\b1080p\\b") -> "1080p"
            has("\\b720p\\b") -> "720p"
            has("\\b(480p|576p|sd)\\b") -> "SD"
            else -> null
        }
        val source = when {
            has("remux") -> "REMUX"
            has("blu-?ray|bdrip|brrip") -> "BluRay"
            has("web-?dl") -> "WEB-DL"
            has("web-?rip") -> "WEBRip"
            has("\\bweb\\b") -> "WEB"
            has("hdtv") -> "HDTV"
            has("\\b(cam|telesync|\\bts\\b)") -> "CAM"
            else -> null
        }
        val hdr = listOfNotNull(
            "DV".takeIf { has("\\b(dv|dovi|dolby ?vision)\\b") },
            when { has("hdr10\\+|hdr10plus") -> "HDR10+"; has("\\bhdr(10)?\\b") -> "HDR"; else -> null },
        ).joinToString(" ").ifEmpty { null }
        val codec = when { has("\\b(hevc|x265|h\\.?265)\\b") -> "HEVC"; has("\\b(avc|x264|h\\.?264)\\b") -> "AVC"; has("\\bav1\\b") -> "AV1"; else -> null }
        val audio = when {
            has("atmos") -> "Atmos"
            has("truehd") -> "TrueHD"
            has("dts-?hd|dts-?x") -> "DTS-HD"
            has("\\bdts\\b") -> "DTS"
            has("dd\\+|ddp|e-?ac-?3") -> "DD+"
            has("\\b(dd|ac-?3)\\b") -> "DD"
            has("\\baac\\b") -> "AAC"
            else -> null
        }?.let { a -> Regex("\\b([257]\\.1)\\b").find(t)?.groupValues?.get(1)?.let { "$a $it" } ?: a }
        val size = Regex("(\\d+(?:[.,]\\d+)?)\\s?(GB|GiB|MB|MiB)\\b", RegexOption.IGNORE_CASE).find(t)?.let { m ->
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
            Regex("nzb|usenet|easynews", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "Usenet"
            Regex("torrent|\\b(rd|pm|tb|ad)\\b|real-?debrid|premiumize|torbox|alldebrid", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "Torrent"
            else -> s.addon
        }
        return kind + if (cached) " ⚡" else ""
    }
}
