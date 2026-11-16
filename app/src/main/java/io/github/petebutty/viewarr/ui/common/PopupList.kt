package io.github.petebutty.viewarr.ui.common

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * A pop-up's list of choices: never taller than [maxFraction] of the screen, scrolling with the highlight (the
 * highlighted choice is always brought into view). Every pop-up list uses it, so none runs off the bottom of the
 * screen with choices you can't reach or see (Remote buttons' long lists did).
 */
@Composable
fun Modifier.popupScroll(maxFraction: Float = 0.7f): Modifier =
    heightIn(max = (popupScreenHeightDp() * maxFraction).dp).verticalScroll(rememberScrollState())

/** The screen's height in Viewarr's own units (its layout size can differ from the system's: AppRoot's TV sizing). */
@Composable
private fun popupScreenHeightDp(): Float = with(androidx.compose.ui.platform.LocalDensity.current) {
    androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.heightPixels.toFloat().toDp().value
}
