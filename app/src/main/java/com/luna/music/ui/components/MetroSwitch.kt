package com.luna.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.MetroRegular
import com.metrocompose.MetroTheme
import com.luna.music.R

/**
 * The Windows Phone 8 toggle switch, drawn the way the phone drew it.
 *
 * Three parts, all square-cornered:
 *
 *  - **the track**, a rectangle outlined in the foreground colour;
 *  - **the fill** inside it, set in from the outline by a gap of nothing at all, which is the accent
 *    when the switch is on and empty when it is off;
 *  - **the thumb**, a solid bar of the foreground colour, *taller than the track* so it stands proud
 *    of it above and below, with a gap of nothing on either side that cuts the outline and the fill
 *    where it sits.
 *
 * Off, the thumb rests at the left end and the track is an empty outline; on, it rests at the right
 * and the track behind it is full of accent. The framework's own toggle puts a small block floating
 * inside a bordered box, which is the outline of the idea and not the control.
 *
 * The gaps are *transparent*, not painted in the page colour as the phone did on its flat black or
 * white: this app's pages sit on album art, and a background-coloured gap would be a grey stripe on
 * a photograph. They are cut out of an offscreen layer instead, so whatever is behind shows through.
 *
 * One [progress] drives the thumb and the fill together, so during the slide the accent follows the
 * thumb along the track instead of snapping on at either end.
 */
@Composable
fun MetroSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: ((Boolean) -> Unit)? = null
) {
    val colors = MetroTheme.colors
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "switch"
    )
    val outline = if (enabled) colors.fg else colors.dim
    val fill = if (enabled) colors.accent else colors.dim

    val clickable = if (onChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onValueChange = onChange
        )
    } else {
        Modifier
    }

    Canvas(
        modifier
            .then(clickable)
            .size(width = TrackWidth, height = ThumbHeight)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val stroke = OutlineWidth.toPx()
        val gap = Gap.toPx()
        val trackH = TrackHeight.toPx()
        val trackTop = (size.height - trackH) / 2f
        val thumbW = ThumbWidth.toPx()
        val thumbX = (size.width - thumbW) * progress

        // The outline, drawn inside its own bounds.
        drawRect(
            color = outline,
            topLeft = Offset(stroke / 2f, trackTop + stroke / 2f),
            size = Size(size.width - stroke, trackH - stroke),
            style = Stroke(stroke)
        )
        // The fill, inset by the outline and the gap, running from the left end to the thumb.
        val inset = stroke + gap
        val fillRight = thumbX + thumbW / 2f
        if (fillRight > inset) {
            drawRect(
                color = fill,
                topLeft = Offset(inset, trackTop + inset),
                size = Size(fillRight - inset, trackH - 2 * inset)
            )
        }
        // The gap either side of the thumb, cut through everything drawn so far.
        drawRect(
            color = Color.Black,
            topLeft = Offset(thumbX - gap, 0f),
            size = Size(thumbW + 2 * gap, size.height),
            blendMode = BlendMode.Clear
        )
        // The thumb, the full height of the control.
        drawRect(color = outline, topLeft = Offset(thumbX, 0f), size = Size(thumbW, size.height))
    }
}

/**
 * A setting that is a switch: the title, the switch's state in words under it, and the switch at
 * the right — the layout of every switch on the phone's own settings pages. The whole row is the
 * target, so the words can be tapped as well as the switch.
 *
 * The app's replacement for the framework's `SettingRow`, which draws the framework's toggle and
 * writes "On" and "Off" in English whatever the phone's language.
 */
@Composable
fun SettingRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    val colors = MetroTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                color = if (enabled) colors.fg else colors.dim,
                fontFamily = MetroRegular,
                fontSize = 22.sp
            )
            Text(
                text = stringResource(if (checked) R.string.switch_on else R.string.switch_off),
                color = colors.subtle,
                fontFamily = MetroRegular,
                fontSize = 14.sp
            )
        }
        // The row already answers the tap; the switch only has to show it.
        MetroSwitch(checked = checked, enabled = enabled)
    }
}

/** Proportions of the phone's switch: an 88 × 32 track and a 20 × 36 thumb, at about 0.7. */
private val TrackWidth = 62.dp
private val TrackHeight = 23.dp
private val ThumbWidth = 14.dp
private val ThumbHeight = 27.dp
private val OutlineWidth = 2.dp
private val Gap = 3.dp
