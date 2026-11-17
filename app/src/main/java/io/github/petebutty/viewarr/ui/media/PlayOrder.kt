package io.github.petebutty.viewarr.ui.media

import io.github.petebutty.viewarr.ui.theme.AppColors
import io.github.petebutty.viewarr.ui.theme.UiOpts

/**
 * Settings → Playback → Streams → Play order: where Play looks, in turn, for a film or episode. Standard: your own
 * servers (Plex, Jellyfin, Emby), then IPTV (your provider's films and series), then your addons.
 */
object PlayOrder {
    enum class Source { Servers, Iptv, Addons }

    /** The choices, in the order they're listed in Settings (the first is standard). */
    val orders = listOf(
        listOf(Source.Servers, Source.Iptv, Source.Addons),
        listOf(Source.Servers, Source.Addons, Source.Iptv),
        listOf(Source.Iptv, Source.Servers, Source.Addons),
        listOf(Source.Iptv, Source.Addons, Source.Servers),
        listOf(Source.Addons, Source.Servers, Source.Iptv),
        listOf(Source.Addons, Source.Iptv, Source.Servers),
    )

    fun label(o: List<Source>) = o.joinToString(", then ") { when (it) { Source.Servers -> "your servers"; Source.Iptv -> "IPTV"; Source.Addons -> "addons" } }
        .replaceFirstChar { it.uppercase() }

    fun current(): List<Source> = orders.getOrElse(AppColors.palette.opt(UiOpts.PLAY_ORDER)) { orders[0] }
}
