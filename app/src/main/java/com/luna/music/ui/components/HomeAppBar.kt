package com.luna.music.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrocompose.MetroBarRiseMillis
import com.luna.music.R
import kotlin.math.sqrt

/**
 * The home panorama's application bar: WP8's own, **minimized**, as the design guidelines for the
 * Panorama control ask of any app bar on a panorama.
 *
 * Shut, it is a thin strip of the phone's chrome grey with the "…" at its right end, which is all the
 * height the panorama gives up to it; the lists want every pixel of the rest. Tapping the strip slides
 * it up to show its button with the caption under it, and using the button, tapping outside, or Back
 * puts it away again. See [ApplicationBar].
 *
 * @param insetNavigationBar whether this bar is the last thing on the screen and has to clear the
 *   gesture bar itself. Animated, so the bar does not jump when that changes.
 */
@Composable
fun HomeAppBar(
    onSearch: () -> Unit,
    insetNavigationBar: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom by animateDpAsState(
        targetValue = if (insetNavigationBar) navigationBar else 0.dp,
        animationSpec = tween(MetroBarRiseMillis),
        label = "app bar inset"
    )
    val search = stringResource(R.string.action_search)
    ApplicationBar(
        actions = listOf(
            AppBarAction(label = search, icon = { tint -> SearchGlyph(tint) }, onClick = onSearch)
        ),
        mode = ApplicationBarMode.Minimized,
        bottomPadding = bottom,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
    )
}

/** How tall the home screen's bar is at rest — shut, minimized — without the gesture bar under it. */
val HomeAppBarRestingHeight: Dp = MinimizedHeight

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

/**
 * How much room the home screen keeps clear at its bottom for the application bar, which the shell
 * draws over the bottom of the screen rather than giving a place in the layout (so that no page ever
 * changes height when it comes and goes).
 */
val LocalHomeBarSpace = compositionLocalOf { 0.dp }
