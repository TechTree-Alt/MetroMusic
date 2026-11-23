package com.luna.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.metrocompose.MetroBannerLingerMillis
import com.metrocompose.MetroSlideInEasing
import com.metrocompose.MetroTheme
import kotlinx.coroutines.delay

private const val DropInMillis = 250
private const val DropOutMillis = 200

/** Leaves the way it came, gathering speed: the mirror of the framework's slide-in. */
private val DropOutEasing = CubicBezierEasing(0.7f, 0f, 0.9f, 0.4f)

private object TopEdge : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
}

/**
 * The strip WP8 drops over the top of the screen — the framework's `MetroTopBanner`, with the same
 * signature, moving as **one solid piece**.
 *
 * The framework's version did three things at once: the strip faded in at its full height while its
 * contents slid down inside it, and on the way out it faded and slid together on an easing made for
 * arriving. What the eye got was a grey box condensing into black with text sliding about in it.
 * Here nothing fades: the whole strip — its colour, the band under the clock and its contents — is
 * one layer that comes down from above the screen, decelerating, and goes back up, accelerating.
 * The popup window clips it at the top edge of the screen, so it reads as coming out from behind it.
 *
 * [onHide] fires once [lingerMillis] has passed with [visible] still true; changing [resetKey]
 * restarts the wait. The wait is a plain delay, so it holds with the system's animations turned off
 * too — the framework's skipped it then, and the banner never left.
 */
@Composable
fun DropBanner(
    visible: Boolean,
    onHide: () -> Unit,
    resetKey: Any? = Unit,
    lingerMillis: Int = MetroBannerLingerMillis,
    content: @Composable ColumnScope.() -> Unit
) {
    // How far down it is, 0 (above the screen) to 1 (in place).
    val progress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            progress.animateTo(1f, tween(DropInMillis, easing = MetroSlideInEasing))
        } else {
            progress.animateTo(0f, tween(DropOutMillis, easing = DropOutEasing))
        }
    }

    LaunchedEffect(visible, resetKey) {
        if (!visible) return@LaunchedEffect
        delay(lingerMillis.toLong())
        onHide()
    }

    if (!visible && progress.value <= 0f) return

    Popup(
        popupPositionProvider = TopEdge,
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Read in the draw phase, so the movement redraws a layer and recomposes nothing.
                .graphicsLayer { translationY = -(1f - progress.value) * size.height }
                // Over the status bar: the colour reaches the top of the screen and moves with the
                // rest, while the content starts under the clock.
                .background(MetroTheme.colors.bg)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            content = content
        )
    }
}
