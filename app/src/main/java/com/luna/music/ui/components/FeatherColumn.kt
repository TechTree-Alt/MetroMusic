package com.luna.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout

/**
 * A column whose items turn in one after another when it first appears — the Windows Phone
 * TurnstileFeatherEffect, the entrance of every settings page on the phone.
 *
 * With the Toolkit's numbers (see Turnstile.kt): each item swings in −80° → 0° over 350ms with the
 * exponential ease-out, the next one 40ms later, top to bottom, and all of them about one hinge a fifth
 * of the screen's width out beyond the left edge — so the page opens like a door made of slats rather
 * than each row spinning about its own corner. An item is invisible until its turn comes.
 *
 * [startIndex] is where this column's items fall in the page's order, for a page whose header is a
 * separate column above a scrolling one: the header takes the first turns and the rows follow on.
 * [delayMillis] holds the whole entrance back — by the old page's turn-out, when the page arrives by
 * navigation, so the slats start as the previous page is gone.
 *
 * Laid out like a plain `Column` with start alignment. The turning is only in how each item is drawn
 * (`placeWithLayer`), so nothing re-measures while it plays, and it plays once.
 */
@Composable
fun FeatherColumn(
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    delayMillis: Int = TurnOutMillis,
    content: @Composable () -> Unit
) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val total = delayMillis + MaxStaggered * StaggerMillis + TurnInMillis
        clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }

    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables.sumOf { it.height }.coerceAtLeast(constraints.minHeight)
        // The hinge, a fifth of the page's width out to the left of it.
        val hingeX = HingeShare * width
        layout(width, height) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                val order = (startIndex + index).coerceAtMost(MaxStaggered)
                placeable.placeWithLayer(0, y) {
                    val elapsed = clock.value - delayMillis - order * StaggerMillis
                    if (elapsed < 0f) {
                        alpha = 0f
                    } else {
                        val t = TurnEaseOut.transform(elapsed / TurnInMillis)
                        alpha = 1f
                        val itemWidth = size.width.coerceAtLeast(1f)
                        transformOrigin = TransformOrigin(hingeX / itemWidth, 0.5f)
                        rotationY = ForwardInDegrees * (1f - t)
                        cameraDistance = CameraWidths * width / CameraUnit
                    }
                }
                y += placeable.height
            }
        }
    }
}

/** The feather's hinge, as a share of the page's width, measured from its left edge (outwards). */
private const val HingeShare = -0.2f

/** How far behind the item above each one starts, going forward. */
private const val StaggerMillis = 40

/** Items past this many start with the last of them, so a long page does not take seconds. */
private const val MaxStaggered = 15
