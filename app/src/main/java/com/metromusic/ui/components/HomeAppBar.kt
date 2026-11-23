package com.metromusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.MetroBarRiseMillis
import com.metrocompose.MetroRegular
import com.metrocompose.MetroTheme
import com.metromusic.R
import kotlin.math.sqrt

/**
 * The home panorama's application bar: WP8's own, in its default compact form.
 *
 * A strip along the bottom with the buttons as bare rings and an ellipsis at its right end; the ring
 * carries the meaning and the caption is one tap away. Tapping the ellipsis slides the bar up to show
 * each button's lowercase label under it — the phone's way of explaining an icon without spending a
 * line of the panorama on words all the time — and using a button puts it away again. The detail
 * pages use the framework's always-labelled `AppBar`; this is the panorama, where height is the
 * scarce thing and the lists want every pixel of it.
 *
 * The bar is drawn in the phone's chrome grey rather than the page background, slightly translucent,
 * so it reads as system furniture laid over the wallpaper and not as one more row of the list.
 *
 * @param insetNavigationBar whether this bar is the last thing on the screen and has to clear the
 *   gesture bar itself. Not when the now-playing strip is showing: that strip sits under this one and
 *   already carries the inset, so padding here as well would open a gap between the two. Animated in
 *   step with the strip coming and going, so the bar does not jump when a song starts or ends.
 */
@Composable
fun HomeAppBar(
    onSearch: () -> Unit,
    insetNavigationBar: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MetroTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chrome = if (colors.bg.luminance() < 0.5f) DarkChrome else LightChrome

    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom by animateDpAsState(
        targetValue = if (insetNavigationBar) navigationBar else 0.dp,
        animationSpec = tween(MetroBarRiseMillis),
        label = "app bar inset"
    )

    Box(
        modifier
            .fillMaxWidth()
            .background(chrome.copy(alpha = ChromeAlpha))
            .padding(bottom = bottom)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HomeAppBarButton(
                label = stringResource(R.string.action_search),
                expanded = expanded,
                onClick = {
                    expanded = false
                    onSearch()
                }
            ) { tint -> SearchGlyph(tint) }
        }

        // The ellipsis sits in the top right corner, as it does on the phone, with a whole finger's
        // worth of target around three small dots.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(width = 56.dp, height = 40.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.appbar_more),
                    role = Role.Button
                ) { expanded = !expanded },
            contentAlignment = Alignment.Center
        ) {
            Ellipsis(colors.fg)
        }
    }
}

/**
 * One button of [HomeAppBar]: a 2dp ring with the glyph inside, the label underneath only while the
 * bar is open. Pressed, the ring fills with the accent and the glyph turns white — WP8's press state,
 * which the bar needs because a ring with no caption is otherwise a small target with no answer.
 */
@Composable
private fun HomeAppBarButton(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    glyph: @Composable (tint: Color) -> Unit
) {
    val colors = MetroTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (pressed) Color.White else colors.fg

    Column(
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClickLabel = label,
            role = Role.Button,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(RingSize)
                .then(if (pressed) Modifier.background(colors.accent, CircleShape) else Modifier)
                .border(2.dp, if (pressed) colors.accent else tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            glyph(tint)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(LabelMillis)) + fadeIn(tween(LabelMillis)),
            exit = shrinkVertically(tween(LabelMillis)) + fadeOut(tween(LabelMillis / 2))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(5.dp))
                Text(label, color = colors.subtle, fontFamily = MetroRegular, fontSize = 11.sp)
            }
        }
    }
}

/**
 * The WP8 search mark, drawn rather than typed: a lens with its handle running down to the *left*,
 * the way the phone's own search button had it, at the 2dp stroke every ring and line icon in the
 * framework uses. U+1F50D is the obvious character and arrives as a colour emoji; U+2315 is in almost
 * no font.
 */
@Composable
private fun SearchGlyph(tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val radius = this.size.width * 0.32f
        val center = Offset(this.size.width * 0.60f, this.size.height * 0.40f)
        drawCircle(color = tint, radius = radius, center = center, style = Stroke(stroke))
        // From the rim of the lens, down and to the left, at forty-five degrees.
        val diagonal = (1f / sqrt(2f))
        val rim = Offset(center.x - radius * diagonal, center.y + radius * diagonal)
        drawLine(
            color = tint,
            start = rim,
            end = Offset(this.size.width * 0.08f, this.size.height * 0.92f),
            strokeWidth = stroke * 1.25f,
            cap = StrokeCap.Square
        )
    }
}

/** Three dots, the app bar's "there is more here". */
@Composable
private fun Ellipsis(tint: Color) {
    Canvas(Modifier.size(width = 22.dp, height = 4.dp)) {
        val r = size.height / 2f
        val step = (size.width - 2 * r) / 2f
        repeat(3) { i -> drawCircle(tint, r, Offset(r + step * i, r)) }
    }
}

/** The phone's application-bar greys: #1F1F1F under the dark theme, #DDDDDD under the light one. */
private val DarkChrome = Color(0xFF1F1F1F)
private val LightChrome = Color(0xFFDDDDDD)

/** Enough to read as a surface over album art, little enough that the wallpaper still shows. */
private const val ChromeAlpha = 0.9f

/**
 * Smaller than the framework's 48dp `AppBarButton` ring: this is the compact bar, and the ring is
 * all of it there is until the ellipsis opens it.
 */
private val RingSize = 40.dp

private const val LabelMillis = 200
