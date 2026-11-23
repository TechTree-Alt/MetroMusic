package com.luna.music.ui.components

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.metrocompose.LocalMetroAnimScope
import kotlin.math.exp

/*
 * The Windows Phone turnstile, with the phone's own numbers.
 *
 * They are the values in Microsoft's published Windows Phone Toolkit (Ms-PL) — the storyboards in
 * Transitions/Storyboards/Turnstile*.xaml and Effects/TurnstileFeatherEffect.cs — as NewSwipe's Metro
 * mode carries them over:
 *
 *  - a page turns about its **left edge**, like a door on its hinge;
 *  - going forward, the old page turns away 0° → 50° in 250ms, accelerating, and vanishes at the end;
 *    then the new one swings in −80° → 0° in 350ms, decelerating;
 *  - going back, the mirror: the old page turns out 0° → −80°, the one returned to comes in 50° → 0°;
 *  - the easing is Silverlight's ExponentialEase with exponent 6 — slow then fast going out, fast then
 *    gentle coming in;
 *  - the camera stands 2.2 screen widths back, like Silverlight's PlaneProjection, so the edge that
 *    swings towards you at −80° grows without ballooning;
 *  - a feathered page (see FeatherColumn) turns its items in one by one instead, 40ms apart going
 *    forward and 50ms coming back, all about one hinge a fifth of the screen out to the left.
 *
 * Android's rotationY turns the right edge into the screen when positive, which matches the Toolkit's
 * PlaneProjection signs as NewSwipe uses them.
 */

internal const val TurnOutMillis = 250
internal const val TurnInMillis = 350
internal const val TurnTotalMillis = TurnOutMillis + TurnInMillis

internal const val ForwardInDegrees = -80f
internal const val ForwardOutDegrees = 50f
internal const val BackwardInDegrees = 50f
internal const val BackwardOutDegrees = -80f

/** How far back the camera is, in screen widths. */
internal const val CameraWidths = 2.2f

/** ExponentialEase, exponent 6. */
internal class ExponentialEase(private val exponent: Double, private val easeOut: Boolean) : Easing {
    private val denominator = exp(exponent) - 1
    private fun easeIn(t: Double) = (exp(exponent * t) - 1) / denominator
    override fun transform(fraction: Float): Float = when {
        fraction <= 0f -> 0f
        fraction >= 1f -> 1f
        easeOut -> (1 - easeIn(1.0 - fraction)).toFloat()
        else -> easeIn(fraction.toDouble()).toFloat()
    }
}

internal val TurnEaseOut = ExponentialEase(6.0, easeOut = true)
internal val TurnEaseIn = ExponentialEase(6.0, easeOut = false)

/**
 * Turns a page of the navigation host in and out like a Windows Phone page.
 *
 * Wrap each destination's content in it. It reads the page's own enter/exit animation from the host
 * and draws the turn from that, so the host's transition only has to keep both pages composed for the
 * length of the turn.
 *
 * [popping] is whether this navigation is going back. [turnsIn] false is for a page that brings its
 * own entrance — a feathered page, whose items turn in one by one: it is held invisible while the old
 * page turns out, then simply shown, and its items do the turning.
 */
@Composable
fun TurnstilePage(popping: Boolean, turnsIn: Boolean = true, content: @Composable () -> Unit) {
    val scope = LocalMetroAnimScope.current
    if (scope == null) {
        content()
        return
    }
    // 0 → 1 while this page is coming in, 1 at rest, 1 → 2 while it is going out — linear in time,
    // over the whole turn, so the phases below are read off it in milliseconds.
    val phase by scope.transition.animateFloat(
        transitionSpec = { tween(TurnTotalMillis, easing = LinearEasing) },
        label = "turnstile"
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> 0f
            EnterExitState.Visible -> 1f
            EnterExitState.PostExit -> 2f
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = phase
                transformOrigin = TransformOrigin(0f, 0.5f)
                cameraDistance = CameraWidths * size.width / CameraUnit
                when {
                    // Coming in: hidden while the old page turns out, then the swing.
                    p < 1f -> {
                        val elapsed = p * TurnTotalMillis
                        if (elapsed < TurnOutMillis) {
                            alpha = 0f
                        } else {
                            alpha = 1f
                            if (turnsIn) {
                                val t = TurnEaseOut.transform((elapsed - TurnOutMillis) / TurnInMillis)
                                val from = if (popping) BackwardInDegrees else ForwardInDegrees
                                rotationY = from * (1f - t)
                            }
                        }
                    }
                    // Going out: the swing, gone for its last 4%, then nothing for the rest.
                    p > 1f -> {
                        val elapsed = (p - 1f) * TurnTotalMillis
                        if (elapsed >= TurnOutMillis) {
                            alpha = 0f
                        } else {
                            val linear = elapsed / TurnOutMillis
                            val to = if (popping) BackwardOutDegrees else ForwardOutDegrees
                            rotationY = to * TurnEaseIn.transform(linear)
                            alpha = if (linear >= 0.96f) 0f else 1f
                        }
                    }
                }
            }
    ) {
        content()
    }
}

/**
 * The camera distance unit of a graphics layer: a layer's `cameraDistance` is handed to the render
 * node as is, and the render node reads it in inches of 72 units — so a distance of D pixels is D / 72.
 */
internal const val CameraUnit = 72f
