package io.github.petebutty.viewarr.data.media

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Choosing between the copies of a film on your servers (4K or 1080p, Dolby Vision or SDR, Atmos or stereo): the
 * best one this TV can show. Play takes the top one; Play from (hold OK on Play) lists them all, best first.
 */
object VersionPick {
    /** What the TV and box can show: the tallest picture allowed (Best quality allowed), and which HDR kinds. */
    data class Screen(val maxHeight: Int, val dolbyVision: Boolean, val hdr10: Boolean, val hdr10Plus: Boolean, val hlg: Boolean)

    @Suppress("DEPRECATION")
    fun screen(context: Context, maxHeight: Int): Screen {
        val d = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val types = runCatching { d?.hdrCapabilities?.supportedHdrTypes?.toSet() }.getOrNull().orEmpty()
        return Screen(
            maxHeight = maxHeight,
            dolbyVision = Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in types && DeviceCodecs.has("video/dolby-vision"),
            hdr10 = Display.HdrCapabilities.HDR_TYPE_HDR10 in types,
            hdr10Plus = Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in types,
            hlg = Display.HdrCapabilities.HDR_TYPE_HLG in types,
        )
    }

    /** The box can decode it itself (otherwise only Plex can play it, by converting). */
    fun decodes(v: CopyVersion) = DeviceCodecs.canPlay(v.videoCodec, v.container)

    /** Higher is better; copies with nothing known score 0. [home]: on a server in this house (no internet in between). */
    fun score(v: CopyVersion?, s: Screen, home: Boolean): Int {
        var n = if (home) 1_500 else 0
        v ?: return n
        val h = v.height ?: 0
        if (!decodes(v)) n -= 100_000
        // Above Best quality allowed (or the TV): only if there's nothing else.
        if (h > s.maxHeight + 100) n -= 50_000
        n += minOf(h, s.maxHeight) * 10
        n += when (v.hdr) {
            "Dolby Vision" -> when { s.dolbyVision -> 3_000; v.dvOnly -> -20_000; s.hdr10 -> 2_000; else -> -1_500 }
            "HDR10+" -> when { s.hdr10Plus -> 2_500; s.hdr10 -> 2_000; else -> -1_500 }
            "HDR10" -> if (s.hdr10) 2_000 else -1_500
            "HLG" -> if (s.hlg) 1_000 else -1_000
            else -> 0
        }
        if (v.atmos) n += 800
        n += (v.audioChannels ?: 0).coerceAtMost(8) * 100
        n += ((v.kbps ?: 0) / 1_000).coerceAtMost(150)
        return n
    }

    /** Why a copy isn't the obvious choice, for the Play from list (null: nothing to say). */
    fun note(v: CopyVersion?, s: Screen, plex: Boolean): String? {
        v ?: return null
        return when {
            !decodes(v) -> if (plex) "Plex converts it" else "This box can't play it"
            v.hdr == "Dolby Vision" && v.dvOnly && !s.dolbyVision -> "Wrong colours here (Dolby Vision only)"
            (v.height ?: 0) > s.maxHeight + 100 -> "Above ${if (s.maxHeight >= 2000) "4K" else "${s.maxHeight}p"}"
            v.hdr != null && v.hdr != "Dolby Vision" && !s.hdr10 && !s.hlg -> "Shown without HDR"
            else -> null
        }
    }

    /** "TrueHD", "DTS-HD MA"… from a server's codec name. */
    fun audioName(codec: String?, profile: String? = null): String? {
        val c = codec?.lowercase() ?: return null
        val p = profile?.lowercase().orEmpty()
        return when {
            c == "truehd" -> "TrueHD"
            c == "eac3" -> "DD+"
            c == "ac3" -> "Dolby Digital"
            c == "dca" || c == "dts" -> when { "ma" in p -> "DTS-HD MA"; "x" in p -> "DTS:X"; else -> "DTS" }
            c == "aac" -> "AAC"
            c == "flac" -> "FLAC"
            c == "opus" -> "Opus"
            c.startsWith("pcm") -> "PCM"
            else -> c.uppercase()
        }
    }

    fun channelsName(ch: Int?) = when (ch) { null, 0 -> null; 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${ch}ch" }

    /** "Atmos 7.1", "DTS-HD MA 5.1", "AAC Stereo". */
    fun audioLabel(codec: String?, profile: String?, channels: Int?, atmos: Boolean): String? {
        val ch = channelsName(channels)
        return if (atmos) listOfNotNull("Atmos", ch).joinToString(" ") else listOfNotNull(audioName(codec, profile), ch).joinToString(" ").ifEmpty { null }
    }
}
