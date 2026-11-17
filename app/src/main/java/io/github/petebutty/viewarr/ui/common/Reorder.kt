package io.github.petebutty.viewarr.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Which way a list being reordered runs: ▲ ▼ move in a [Vertical] list, ◀ ▶ in a [Horizontal] row. */
enum class ReorderAxis { Vertical, Horizontal }

/** The one "moving" hint, for under the list while something is carried. */
fun movingHint(axis: ReorderAxis = ReorderAxis.Vertical): String =
    if (axis == ReorderAxis.Vertical) "Moving: ▲ ▼ to place it  ·  OK: put it down"
    else "Moving: ◀ ▶ to place it  ·  OK: put it down"

/**
 * Which entry of a list is picked up (being moved), by id. One per list: `val carry = rememberCarry()`, then each row
 * `Modifier.reorderable(carried = carry.isCarried(id), …, onDrop = carry::drop)`. Hold-OK menus pick up with
 * [moveOption] (OptionsPopup's "Move"), or call [pickUp] straight from a row's onLongClick.
 */
@Stable
class Carry {
    var id by mutableStateOf<String?>(null)
        private set
    val active: Boolean get() = id != null
    fun isCarried(id: String): Boolean = this.id == id
    fun pickUp(id: String) { this.id = id }
    fun drop() { id = null }
}

@Composable
fun rememberCarry(): Carry = remember { Carry() }

/**
 * Moving an entry with the remote, the same everywhere. While [carried]: the arrows along [axis] call [onMove] (-1
 * back / up, +1 on / down); OK, Back, or (vertical lists) Left put it down ([onDrop]); the other arrows do nothing, so
 * the highlight stays on it. Back is fully taken (down and up), so it never also leaves the page; a parent's own
 * onPreviewKeyEvent sees keys first, though, so a page-level Back handler must check its carry itself.
 * The OK that picked it up (hold OK on the row) is ignored, repeats and release: only a fresh OK puts it down.
 * [rightToggles]: Settings lists' shortcut, Right picks up ([onPickUp]) and puts down.
 * Put it after the row's focusRequester and before its Surface (it must be on the focused thing or a parent of it).
 */
@Composable
fun Modifier.reorderable(
    carried: Boolean,
    onMove: (Int) -> Unit,
    onDrop: () -> Unit,
    axis: ReorderAxis = ReorderAxis.Vertical,
    rightToggles: Boolean = false,
    onPickUp: () -> Unit = {},
): Modifier {
    // A fresh OK seen since it was picked up (until then OK belongs to the press that picked it up).
    val armed = remember(carried) { mutableStateOf(false) }
    // The rest of a press that put it down (its release, Back's release): swallowed, so the row isn't clicked and
    // the page doesn't go back.
    val swallow = remember { mutableStateOf<Key?>(null) }
    return this.onPreviewKeyEvent { e ->
        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        val k = if (ok) Key.DirectionCenter else e.key
        val down = e.type == KeyEventType.KeyDown
        val fresh = down && e.nativeKeyEvent.repeatCount == 0
        swallow.value?.let { s ->
            if (s == k) { if (e.type == KeyEventType.KeyUp) swallow.value = null; return@onPreviewKeyEvent true }
            swallow.value = null
        }
        if (!carried) {
            if (rightToggles && e.key == Key.DirectionRight && down) { if (fresh) onPickUp(); return@onPreviewKeyEvent true }
            return@onPreviewKeyEvent false
        }
        fun drop(): Boolean { swallow.value = k; onDrop(); return true }
        val back = if (axis == ReorderAxis.Vertical) Key.DirectionUp else Key.DirectionLeft
        val on = if (axis == ReorderAxis.Vertical) Key.DirectionDown else Key.DirectionRight
        when {
            ok -> when {
                fresh -> { armed.value = true; drop() }
                // The pick-up press's repeats: taken. Its release: left to the row (it knows it was a long press).
                !armed.value && e.type == KeyEventType.KeyUp -> false
                else -> true
            }
            e.key == Key.Back -> if (fresh) drop() else true
            e.key == back -> { if (down) onMove(-1); true }
            e.key == on -> { if (down) onMove(1); true }
            axis == ReorderAxis.Vertical && e.key == Key.DirectionLeft -> if (fresh) drop() else true
            rightToggles && e.key == Key.DirectionRight -> if (fresh) drop() else true
            e.key == Key.DirectionUp || e.key == Key.DirectionDown || e.key == Key.DirectionLeft || e.key == Key.DirectionRight -> true
            else -> false
        }
    }
}

/**
 * Left at a page's left edge opens the menu ([onMenu]), once per press: holding Left to run along a row stops at
 * the edge instead of throwing the menu open (key repeats and the release are taken, not passed on).
 * Put it on the first item of each row / the left-most column. [enabled] false: Left does what it normally does.
 */
fun Modifier.leftOpens(enabled: Boolean = true, onMenu: () -> Unit): Modifier = this.onPreviewKeyEvent { e ->
    if (!enabled || e.key != Key.DirectionLeft) return@onPreviewKeyEvent false
    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) onMenu()
    true
}
