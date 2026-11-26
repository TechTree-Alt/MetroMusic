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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
 *    up to spell out what each of them is and to show the menu.
 *  - [Minimized]: a thin strip with nothing in it but the "…". The SDK documentation says to "use the
 *    mini size on pages where you want to maximize screen space, such as on panorama pages."
 *
 * A bar with no icon buttons at all — only menu items — is drawn minimized whatever it is asked for:
 * a full-height band with nothing on it would be height spent on nothing.
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

/**
 * One text item of an [ApplicationBar]'s menu. The guidelines put here what is used less often, and
 * anything an icon cannot say clearly; the text is lowercase (the platform lowercased it itself) and
 * should stay within 14 to 20 characters.
 */
@Immutable
class AppBarMenuItem(
    val label: String,
    val enabled: Boolean = true,
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
 * The Windows Phone application bar: a band of the phone's chrome colour along the **bottom** of the
 * page, at most four ringed icon buttons spread across it, an optional menu of at most five text items,
 * and the "…" in its top right corner. Put it after the page's content, with the content taking the
 * remaining height: the bar is opaque, so — as the documentation says of an opacity of 1 — the page is
 * resized to the room the bar leaves rather than drawn under it.
 *
 * Captions and menu are not shown until the "…" is tapped. The guide lists every way it is put away
 * again, and all of them work here: tapping the dots, tapping outside the bar (through
 * [LocalAppBarDismissal]), Back, or choosing a button or a menu item.
 *
 * @param bottomPadding how far the bar's content keeps clear of the bottom edge. `null` clears the
 *   navigation bar, which costs nothing inside a page that already has (`MetroPage` consumes it) and
 *   is right on a full-bleed screen where the bar is the last thing; the band itself runs to the edge
 *   either way.
 */
@Composable
fun ApplicationBar(
    actions: List<AppBarAction>,
    modifier: Modifier = Modifier,
    menuItems: List<AppBarMenuItem> = emptyList(),
    mode: ApplicationBarMode = ApplicationBarMode.Default,
    bottomPadding: Dp? = null,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    require(actions.size <= MaxIconButtons) { "an application bar holds at most four icon buttons" }
    require(menuItems.size <= MaxMenuItems) { "an application bar menu holds at most five items" }

    val colors = MetroTheme.colors
    var ownExpanded by rememberSaveable { mutableStateOf(false) }
    val open = expanded ?: ownExpanded
    val setOpen: (Boolean) -> Unit = { value ->
        if (onExpandedChange != null) onExpandedChange(value) else ownExpanded = value
    }
    BackHandler(enabled = open) { setOpen(false) }

    // While open, a tap anywhere outside the bar closes it; the shell does the listening.
    val dismissal = LocalAppBarDismissal.current
    val coordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val close by rememberUpdatedState { setOpen(false) }
    if (open && dismissal != null) {
        DisposableEffect(dismissal) {
            val registration = AppBarDismissal.OpenBar({ coordinates.value }, { close() })
            dismissal.open = registration
            onDispose { if (dismissal.open === registration) dismissal.open = null }
        }
    }

    val chrome = if (colors.bg.luminance() < 0.5f) DarkChrome else LightChrome
    val minimized = mode == ApplicationBarMode.Minimized || actions.isEmpty()
    val toggleLabel = stringResource(R.string.appbar_more)

    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates.value = it }
            .background(chrome)
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
                if (actions.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        actions.forEach { action ->
                            AppBarRingButton(
                                action = action,
                                showLabel = open,
                                onClick = {
                                    setOpen(false)
                                    action.onClick()
                                }
                            )
                        }
                    }
                } else {
                    // Menu only: the dots still need their row to sit in.
                    Spacer(Modifier.height(EllipsisTargetHeight))
                }
                AnimatedVisibility(
                    visible = open && menuItems.isNotEmpty(),
                    enter = expandVertically(tween(LabelMillis)) + fadeIn(tween(LabelMillis)),
                    exit = shrinkVertically(tween(LabelMillis)) + fadeOut(tween(LabelMillis / 2))
                ) {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
                        menuItems.forEach { item ->
                            Text(
                                text = item.label,
                                color = if (item.enabled) colors.fg else colors.dim,
                                fontFamily = MetroRegular,
                                fontSize = MenuItemSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = item.enabled, role = Role.Button) {
                                        setOpen(false)
                                        item.onClick()
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        // The ellipsis sits in the top right corner, as it does on the phone, with a target at least
        // the guide's 7 mm minimum around three small dots.
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
 * Lets an open [ApplicationBar] close on a tap anywhere outside it. The shell provides one
 * ([LocalAppBarDismissal]) and listens with [Modifier.dismissesOpenAppBar] over the whole window; an
 * open bar registers itself here. The tap that closes the bar does nothing else — it is a "close
 * this", and letting it open an album as well would punish the person for putting the menu away.
 */
@Stable
class AppBarDismissal {
    internal class OpenBar(val coordinates: () -> LayoutCoordinates?, val close: () -> Unit)

    internal var open: OpenBar? = null

    /** Where the listening box is, which is what a tap's position is measured against. */
    internal var root: LayoutCoordinates? = null
}

val LocalAppBarDismissal = staticCompositionLocalOf<AppBarDismissal?> { null }

/** The window-wide listener for [AppBarDismissal]; put it on the shell's outermost box. */
fun Modifier.dismissesOpenAppBar(dismissal: AppBarDismissal): Modifier =
    this
        .onGloballyPositioned { dismissal.root = it }
        .pointerInput(dismissal) {
            awaitEachGesture {
                // The initial pass sees the touch before anything under it does.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val bar = dismissal.open ?: return@awaitEachGesture
                val barAt = bar.coordinates()?.takeIf { it.isAttached }
                val rootAt = dismissal.root?.takeIf { it.isAttached }
                if (barAt != null && rootAt != null) {
                    val bounds = rootAt.localBoundingBoxOf(barAt, clipBounds = false)
                    if (bounds.contains(down.position)) return@awaitEachGesture
                }
                down.consume()
                bar.close()
                // And the rest of that touch, so a drag that began as the closing tap scrolls nothing.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
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
                // One line, cut short if it has to be — the platform did the same with a caption.
                Text(
                    text = action.label,
                    color = colors.subtle,
                    fontFamily = MetroRegular,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

/**
 * PhoneChromeColor from the SDK's ThemeResources.xaml: #FF1F1F1F under the dark theme, #FFDDDDDD under
 * the light one. Fully opaque — the documentation recommends only opacities of 0, 0.5 and 1, and an
 * opaque bar is the one that resizes the page instead of covering it, which is what this one does.
 */
private val DarkChrome = Color(0xFF1F1F1F)
private val LightChrome = Color(0xFFDDDDDD)

/** The ring: WP8's 48-pixel icon circle, at the same scale. */
internal val RingSize = 40.dp

private val GlyphSize = 20.sp

/** 48dp: over the guide's 7 mm floor for a touch target. */
private val EllipsisTargetHeight = 48.dp

private val MenuItemSize = 22.sp

private const val MaxIconButtons = 4
private const val MaxMenuItems = 5

private const val LabelMillis = 200
