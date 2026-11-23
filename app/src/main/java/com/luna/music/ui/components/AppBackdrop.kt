package com.luna.music.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrocompose.MetroLight
import com.metrocompose.MetroTheme
import com.luna.music.core.LocalServices

/**
 * The one background the whole app sits on.
 *
 * Installed once by the shell through `MetroBackdrop`, and handed to the home panorama as its
 * parallaxing background layer as well — the same drawing in both places, so going from the panorama
 * into an album keeps the wallpaper it had instead of dropping to a flat colour. Only the movement
 * differs: under the panorama it pans with the finger, under a page it stays put.
 *
 * What it draws is the album that is playing, tinted with the accent and washed into the theme's
 * background so the text over it stays readable. With nothing playing, no cover to show, or the
 * setting off, it is a gradient made from the theme's own background — so light mode, dark mode
 * and a custom colour all look like themselves rather than like the dark theme with other text.
 */
@Composable
fun AppBackdrop() {
    val services = LocalServices.current
    val settings by services.settings.settings.collectAsStateWithLifecycle()
    val playerState by services.player.state.collectAsStateWithLifecycle()

    val cover = rememberAlbumArt(
        albumId = playerState.albumId,
        representativeTrackId = playerState.trackId ?: -1L,
        size = 260.dp
    ).takeIf { settings.artworkBackground && playerState.hasTrack }

    // A change of song is a change of picture, and a cut from one cover to the next across the
    // whole screen is the loudest thing that could happen behind a list. Fading is the Metro way of
    // replacing one surface with another.
    Crossfade(
        targetState = cover,
        modifier = Modifier.fillMaxSize(),
        animationSpec = tween(BackdropFadeMillis),
        label = "backdrop"
    ) {
        if (it != null) ArtworkBackdrop(it) else GradientBackdrop()
    }
}

/**
 * A wide gradient with an oversized note bleeding off the edge, built from the theme's background.
 *
 * The two tints — the WP8 cyan and magenta the dark version always had — are mixed *into* the
 * background rather than painted as fixed colours, so on black they are the deep teal and plum the
 * app shipped with, on white a pale wash of the same, and on a custom colour that colour, faintly
 * lit. Starts and ends on the same colour on purpose: a wrapping panorama tiles its backdrop, and a
 * gradient whose two ends differ shows a hard edge where the copies meet.
 */
@Composable
private fun GradientBackdrop() {
    val colors = MetroTheme.colors
    val light = colors.bg.luminance() > 0.5f
    val amount = if (light) 0.10f else 0.13f
    val cool = lerp(colors.bg, TintCool, amount)
    val warm = lerp(colors.bg, TintWarm, amount)
    val mid = lerp(colors.bg, colors.fg, 0.03f)

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .background(Brush.horizontalGradient(listOf(cool, mid, warm, mid, cool)))
    ) {
        Text(
            text = "♪",
            color = colors.fg.copy(alpha = if (light) 0.06f else 0.10f),
            fontFamily = MetroLight,
            fontSize = 460.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp)
        )
    }
}

/**
 * The playing album's cover as the backdrop: cropped to fill, softened, tinted with the accent, and
 * washed into the theme's background.
 *
 * The softening is a blur on Android 12 and later; before that the cover is a small decode scaled
 * up, which is soft enough by itself. The tint is the accent laid over the picture — the cover
 * keeps its shapes and its light and dark, and takes on the colour the user chose for the app, so
 * the backdrop belongs to the theme instead of fighting it. The wash on top runs from light at the
 * top, where the panorama's title is large enough to survive anything, to nearly solid at the
 * bottom where the rows are: the foreground is text with no plate behind it, and album art is not
 * designed to be read over.
 */
@Composable
private fun ArtworkBackdrop(cover: Bitmap) {
    val colors = MetroTheme.colors
    val light = colors.bg.luminance() > 0.5f
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Image(
            bitmap = cover.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(colors.accent.copy(alpha = TintAlpha), BlendMode.SrcAtop),
            modifier = Modifier.fillMaxSize().blur(24.dp)
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    if (light) {
                        listOf(colors.bg.copy(alpha = 0.55f), colors.bg.copy(alpha = 0.88f))
                    } else {
                        listOf(colors.bg.copy(alpha = 0.45f), colors.bg.copy(alpha = 0.85f))
                    }
                )
            )
        )
    }
}

/** WP8 cyan and magenta, the two tints the gradient mixes into the background. */
private val TintCool = Color(0xFF1BA1E2)
private val TintWarm = Color(0xFFD80073)

/** How much of the accent is laid over the cover: enough to colour it, not enough to hide it. */
private const val TintAlpha = 0.35f

private const val BackdropFadeMillis = 600
