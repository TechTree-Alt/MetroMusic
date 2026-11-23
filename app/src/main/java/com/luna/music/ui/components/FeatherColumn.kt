package com.luna.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

/**
 * A column whose items fly in one after another when it first appears — WP8's "feathered turnstile",
 * the entrance every settings page and list on the phone made.
 *
 * Each item swings in about its own left edge, from turned away to flat, while it fades up and
 * slides the last few dp into place; the next one starts [StaggerMillis] later, top to bottom, so the
 * page reads as a sheet of cards dealt onto the screen rather than one picture appearing. It runs once,
 * when the column is first composed — coming back to a page, or the page changing what it lists, does
 * not replay it.
 *
 * Laid out like a plain `Column` with start alignment: each child at its natural height, one under the
 * other. The animation is only in how each one is drawn (`placeWithLayer`), so nothing re-measures and
 * nothing moves underneath while it plays.
 */
@Composable
fun FeatherColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(TotalMillis.toFloat(), tween(TotalMillis, easing = LinearEasing))
    }
    val slidePx = SlideDp

    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables.sumOf { it.height }.coerceAtLeast(constraints.minHeight)
        val slide = slidePx.dp.toPx()
        layout(width, height) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                placeable.placeWithLayer(0, y) {
                    val elapsed = clock.value - index.coerceAtMost(MaxStaggered) * StaggerMillis
                    val t = Ease.transform((elapsed / ItemMillis).coerceIn(0f, 1f))
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = (1f - t) * StartAngle
                    translationX = (1f - t) * slide
                    alpha = t
                    cameraDistance = 12f * density
                }
                y += placeable.height
            }
        }
    }
}

/** The phone's turnstile entrance: items start turned this far away about their left edge. */
private const val StartAngle = -70f

/** How far each item slides into place as it turns, in dp. */
private const val SlideDp = 40f

/** How long one item takes, and how far behind the one above it it starts. */
private const val ItemMillis = 300f
private const val StaggerMillis = 35f

/** Items past this many start together, so a long page does not take seconds to deal out. */
private const val MaxStaggered = 12

private val TotalMillis = (MaxStaggered * StaggerMillis + ItemMillis).toInt()

/** Decelerating, like every Metro entrance: quick off the mark, settling gently. */
private val Ease = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
