package io.github.petebutty.viewarr.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Opening splash, the "Play Stack" logo animated (Peter, 2026-11-17: the design canvas's "12 · Play Stack, animated"):
 * the three play buttons slide in one after another, back to front; the front one gives a little press with a soft
 * emerald glow; "viewarr" rises in beside them. While the app is still getting ready (the splash can stay up to ~11 s
 * on a slow box), the front button presses again every couple of seconds, so it never looks stuck. AppRoot fades it.
 */
@Composable
fun ViewarrSplash() {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }
    val ease = remember { CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f) }
    // 0 → 1 between [a] and [b] seconds, eased.
    fun span(a: Float, b: Float) = ease.transform(((t - a) / (b - a)).coerceIn(0f, 1f))
    val ins = floatArrayOf(span(0f, 0.45f), span(0.15f, 0.6f), span(0.3f, 0.75f))
    // The press: once at 0.9 s, then every 2.4 s while it waits.
    val pressT = if (t < 0.9f) -1f else (t - 0.9f) % 2.4f
    val press = when {
        pressT < 0f -> 0f
        pressT < 0.15f -> pressT / 0.15f
        pressT < 0.4f -> 1f - (pressT - 0.15f) / 0.25f
        else -> 0f
    }
    val glow = when {
        pressT < 0f -> 0f
        pressT < 0.2f -> pressT / 0.2f * 0.55f
        pressT < 0.8f -> 0.55f * (1f - (pressT - 0.2f) / 0.6f)
        else -> 0f
    }
    val word = span(0.75f, 1.25f)
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0D14)), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Canvas(Modifier.size(170.dp)) {
                // The glow behind the front button as it presses.
                if (glow > 0f) drawCircle(
                    Brush.radialGradient(
                        0f to ViewarrBrand.From.copy(alpha = 0.9f * glow), 1f to Color.Transparent,
                        center = Offset(size.width * 0.35f, size.height / 2f), radius = size.minDimension * 0.55f,
                    ),
                    radius = size.minDimension * 0.55f, center = Offset(size.width * 0.35f, size.height / 2f),
                )
                drawViewarrMark(
                    dx = FloatArray(3) { (ins[it] - 1f) * 70f },
                    alpha = FloatArray(3) { ins[it] },
                    frontScale = 1f + 0.08f * press,
                )
            }
            Text(
                "viewarr", fontFamily = ViewarrBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = 80.sp,
                letterSpacing = (-0.03).em, color = Color.White, maxLines = 1, softWrap = false,
                modifier = Modifier.graphicsLayer { alpha = word; translationY = (1f - word) * 14.dp.toPx() },
            )
        }
    }
}
