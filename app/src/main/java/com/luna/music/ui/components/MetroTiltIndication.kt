package com.luna.music.ui.components

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * WP8's tilt, as the press feedback of every tappable thing in the app.
 *
 * Installed as `LocalIndication`, so every `clickable` and `combinedClickable` that does not ask for
 * something else gets it — list rows, album rows and tiles, menu rows, settings rows — where before
 * they got Compose's default, a grey wash over the pressed rectangle, which is no part of the phone.
 * Things that already draw their own Metro press (the framework's tiles and buttons, which tilt by
 * `metroTilt`; the app bar's ring; the switch) pass `indication = null` and are left alone.
 *
 * What it does is what the phone did: while a finger is down the element tips in 3D *towards* the
 * finger — the side under it goes back into the screen, the far side comes up — and the whole of it
 * sinks a little, then it springs back on release. The angle is smaller the wider the element is, so
 * a full-width list row leans by a few degrees where a square tile leans by ten: the same depth at the
 * edge, rather than the same angle, which is what keeps a row from swinging like a door. It is drawn,
 * not laid out, so pressing never moves anything else.
 */
object MetroTiltIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TiltNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x7117
}

private class TiltNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val pressed = Animatable(0f)
    private var at: Offset = Offset.Unspecified
    private val matrix = Matrix()
    private val from = FloatArray(8)
    private val to = FloatArray(8)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        at = interaction.pressPosition
                        launch { pressed.animateTo(1f, tween(PressInMillis)) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        launch { pressed.animateTo(0f, tween(PressOutMillis)) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = pressed.value
        val w = size.width
        val h = size.height
        if (p <= 0f || !at.isSpecified || w <= 0f || h <= 0f) {
            drawContent()
            return
        }

        // Where the finger is, from -0.5 to 0.5 either way from the middle.
        val nx = (at.x / w - 0.5f).coerceIn(-0.5f, 0.5f)
        val ny = (at.y / h - 0.5f).coerceIn(-0.5f, 0.5f)
        // The same depth at the edge whatever the size: a wide row turns by less.
        val reach = ReachDp.dp.toPx()
        val maxY = (MaxDegrees * reach / w).coerceIn(MinDegrees, MaxDegrees)
        val maxX = (MaxDegrees * reach / h).coerceIn(MinDegrees, MaxDegrees)
        val ay = Math.toRadians((nx * 2f * maxY * p).toDouble())
        val ax = Math.toRadians((ny * 2f * maxX * p).toDouble())
        // Sinks by a few dp overall, not by a share: a share of a full-width row is a lot of row.
        val sink = 1f - min(MaxSink, SinkDp.dp.toPx() / max(w, h)) * p
        val distance = max(w, h) * 2.5f + 600f

        val cx = w / 2f
        val cy = h / 2f
        val corners = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        for (i in 0 until 4) {
            val x = corners[i * 2] - cx
            val y = corners[i * 2 + 1] - cy
            // Into the screen is positive: the side the finger is on goes back.
            val z = (x * sin(ay) + y * sin(ax)).toFloat()
            val px = (x * cos(ay)).toFloat() * sink
            val py = (y * cos(ax)).toFloat() * sink
            val f = distance / (distance + z)
            from[i * 2] = corners[i * 2]
            from[i * 2 + 1] = corners[i * 2 + 1]
            to[i * 2] = cx + px * f
            to[i * 2 + 1] = cy + py * f
        }
        matrix.setPolyToPoly(from, 0, to, 0, 4)

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.concat(matrix)
        }
        drawContent()
        drawIntoCanvas { canvas -> canvas.nativeCanvas.restore() }
    }
}

/** The phone's angle for something about the size of a tile, and the floor for a very wide row. */
private const val MaxDegrees = 10f
private const val MinDegrees = 2f

/** The size at which an element leans by the full [MaxDegrees]; wider ones lean by less. */
private const val ReachDp = 140f

/** How far a pressed element sinks, at most: a few dp, and never more than 4% of it. */
private const val SinkDp = 8f
private const val MaxSink = 0.04f

private const val PressInMillis = 90
private const val PressOutMillis = 160
