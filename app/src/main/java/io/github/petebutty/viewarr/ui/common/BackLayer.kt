package io.github.petebutty.viewarr.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Pop-ups over a page (a hold-OK menu, Sort / Filter, Audio & subtitles, an episode's actions, choice panels…) say
 * they're open here, so Back closes them first. The picture in the corner's Back (full screen) waits until none are
 * open: it's handled before the page sees the key, and used to enlarge the film with the pop-up left open under it.
 */
object BackLayers {
    var open by mutableIntStateOf(0)
        private set

    internal fun add(d: Int) { open = (open + d).coerceAtLeast(0) }
}

/** Marks a pop-up as open while [active] (call it inside the pop-up, or beside it with its condition). */
@Composable
fun BackLayer(active: Boolean = true) {
    DisposableEffect(active) {
        if (active) BackLayers.add(1)
        onDispose { if (active) BackLayers.add(-1) }
    }
}
