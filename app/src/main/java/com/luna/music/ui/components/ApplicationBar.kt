package com.luna.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.music.R
import com.metrocompose.CenteredGlyph
import com.metrocompose.MetroRegular
import com.metrocompose.MetroTheme

/**
 * The two states WP8 gave an application bar.
 *
 *  - [Default]: the icon buttons are showing, as bare rings, and the "…" in the corner slides the bar
 *    up to spell out what each of them is.
 *  - [Minimized]: a thin strip with nothing in it but the "…", for a panorama — the design guidelines
 *    for the Panorama control say a panorama's app bar should be minimized, because the panorama is
 *    the one page where height is the whole point. Tapping the strip opens it the same way.
 */
enum class ApplicationBarMode { Default, Minimized }

/** One icon button of an [ApplicationBar]: its lowercase caption, its glyph, and what it does. */
@Immutable
class AppBarAction(
    val label: String,
    val enabled: Boolean = true,
    val icon: @Composable (tint: Color) -> Unit,
    val onClick: () -> Unit
)

/** An [AppBarAction] whose icon is one character of [com.luna.music.ui.Glyphs]. */
fun glyphAction(
    glyph: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) = AppBarAction(
    label = label,
    enabled = enabled,
    icon = { tint -> CenteredGlyph(glyph, tint, GlyphSize, Modifier.size(RingSize)) },
    onClick = onClick
)

/**
 * The Windows Phone application bar: a band of the phone's chrome grey along the **bottom** of the
 * page, at most four ringed icon buttons spread across it, and the "…" in its top right corner.
 *
 * Where it goes is the guideline's, not a taste: WP8 put a page's commands at the bottom edge, under
 * the thumb, and never between the title and the content. The app used to draw its bars under the page
 * title, which made them read as one more row of the page and pushed the list a bar's height down.
 * Put this after the page's content instead, with the content taking the remaining height.
 *
 * Captions are not shown until the "…" is tapped (the phone's own way of explaining an icon without
 * spending a line of the page on words); using a button, tapping the "…" again or pressing Back puts
 * them away again — Back closes an open application bar before it leaves the page, as it did on the
 * phone.
 *
 * [expanded] can be hoisted, so that a caller who can cover the rest of the screen can close the bar on
 * a tap outside it, which is the last of the phone's ways of putting it away.
 *
 * @param bottomPadding how far the bar's buttons keep clear of the bottom edge. `null` clears the
 *   navigation bar, which costs nothing inside a page that already has (`MetroPage` consumes it) and
 *   is right on a full-bleed screen where the bar is the last thing; the band itself still runs to
 *   the edge either way.
 */
@Composable
fun ApplicationBar(
    actions: List<AppBarAction>,
    modifier: Modifier = Modifier,
    mode: ApplicationBarMode = ApplicationBarMode.Default,
    bottomPadding: Dp? = null,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    val colors = MetroTheme.colors
    var ownExpanded by rememberSaveable { mutableStateOf(false) }
    val open = expanded ?: ownExpanded
    val setOpen: (Boolean) -> Unit = { value ->
        if (onExpandedChange != null) onExpandedChange(value) else ownExpanded = value
    }
    BackHandler(enabled = open) { setOpen(false) }

    val chrome = if (colors.bg.luminance() < 0.5f) DarkChrome else LightChrome
    val minimized = mode == ApplicationBarMode.Minimized
    val toggleLabel = stringResource(R.string.appbar_more)

    Box(
        modifier
            .fillMaxWidth()
            .background(chrome.copy(alpha = ChromeAlpha))
            .then(
                if (bottomPadding != null) Modifier.padding(bottom = bottomPadding)
                else Modifier.navigationBarsPadding()
            )
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize(tween(LabelMillis))) {
            if (minimized && !open) {
                // All there is of a minimized bar while it is shut, and all of it opens the bar: the
                // three dots alone would be a target a few dp across.
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(MinimizedHeight)
                        .clickable(onClickLabel = toggleLabel, role = Role.Button) { setOpen(true) }
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    actions.forEach { action ->
                        AppBarRingButton(
                            action = action,
                            // A minimized bar only ever opens all the way, captions and all.
                            showLabel = open,
                            onClick = {
                                setOpen(false)
                                action.onClick()
                            }
                        )
                    }
                }
            }
        }

        // The ellipsis sits in the top right corner, as it does on the phone, with a whole finger's
        // worth of target around three small dots.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(
                    width = 56.dp,
                    height = if (minimized && !open) MinimizedHeight else EllipsisTargetHeight
                )
                .clickable(onClickLabel = toggleLabel, role = Role.Button) { setOpen(!open) },
            contentAlignment = Alignment.Center
        ) {
            Ellipsis(colors.fg)
        }
    }
}

/**
 * One button of [ApplicationBar]: a 2dp ring with the glyph inside, the caption underneath only while
 * the bar is open. Pressed, the ring fills with the accent and the glyph turns white — WP8's press
 * state. The target is wider and taller than the ring, so the 40dp mark is not the 40dp you must hit.
 */
@Composable
private fun AppBarRingButton(
    action: AppBarAction,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    val colors = MetroTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = when {
        !action.enabled -> colors.dim
        pressed -> Color.White
        else -> colors.fg
    }

    Column(
        Modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = action.enabled,
                onClickLabel = action.label,
                role = Role.Button,
                onClick = onClick
            )
            .widthIn(min = 64.dp)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(RingSize)
                .then(if (pressed) Modifier.background(colors.accent, CircleShape) else Modifier)
                .border(2.dp, if (pressed) colors.accent else tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            action.icon(tint)
        }
        AnimatedVisibility(
            visible = showLabel,
            enter = expandVertically(tween(LabelMillis)) + fadeIn(tween(LabelMillis)),
            exit = shrinkVertically(tween(LabelMillis)) + fadeOut(tween(LabelMillis / 2))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = action.label,
                    color = colors.subtle,
                    fontFamily = MetroRegular,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
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

/**
 * The height of a minimized bar: WP8's 30 of 480 pixels, scaled to a phone of about 400dp across.
 * Public because the shell lays the home screen out around the bar at rest.
 */
val MinimizedHeight = 26.dp

/** The phone's application-bar greys: #1F1F1F under the dark theme, #DDDDDD under the light one. */
private val DarkChrome = Color(0xFF1F1F1F)
private val LightChrome = Color(0xFFDDDDDD)

/** Enough to read as a surface over album art, little enough that the wallpaper still shows. */
private const val ChromeAlpha = 0.9f

/** The ring: WP8's 48-pixel icon circle, at the same scale. */
internal val RingSize = 40.dp

private val GlyphSize = 20.sp

private val EllipsisTargetHeight = 40.dp

private const val LabelMillis = 200
