package com.luna.music.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.metrocompose.MetroCrossfade
import com.metrocompose.MetroEmptyNote
import com.metrocompose.MetroIcon
import com.metrocompose.MetroLight
import com.metrocompose.MetroRegular
import com.metrocompose.MetroSlideInEasing
import com.metrocompose.MetroPageSwipeState
import com.metrocompose.MetroRisingPageState
import com.metrocompose.MetroSwap
import com.metrocompose.MetroSlider
import com.metrocompose.MetroTheme
import com.metrocompose.TransportButton
import com.metrocompose.metroRiseDrag
import com.metrocompose.metroSlideIn
import com.metrocompose.rememberMetroPageSwipe
import com.luna.music.R
import com.luna.music.core.LocalServices
import com.luna.music.data.store.PlayerFace
import com.luna.music.ui.components.pushDownToCollapse
import com.luna.music.ui.components.AlbumArt
import com.luna.music.ui.components.BackdropAlpha
import com.luna.music.ui.components.BackdropScrimBottom
import com.luna.music.ui.components.BackdropScrimTop
import com.luna.music.ui.components.LyricTickMs
import com.luna.music.ui.components.PlayerLyrics
import com.luna.music.ui.components.rememberWordsAvailable
import com.luna.music.ui.components.rememberPlayerWords
import com.luna.music.ui.components.rememberAlbumArt
import com.luna.music.ui.formatDuration
import com.luna.music.ui.nav.Screen

/**
 * Everything in the column that is not the cover, measured: the padding at both ends, the artist
 * and album above the cover, and below it the position bar with its labels, the track title and the
 * button row. The cover gets whatever the window has left over.
 */
private val ChromeHeight = 468.dp

/** How often the position is asked for when the square is showing a cover rather than words. */
private const val SliderTickMs = 500L

/**
 * What the title keeps between itself and the transport once the words have taken the slack.
 *
 * There is no such number in the cover mode and there does not need to be: whatever height the square
 * leaves over is a weighted spacer between the title and the buttons, and on a tall phone that is a
 * good deal of blank page. The words take that page, so this is what is left of it — enough that the
 * title is not jammed against the rings.
 */
private val TransportGap = 20.dp

/**
 * What the artist's name keeps between itself and the status bar in the words mode.
 *
 * The cover mode starts the page 106dp down, which is the phone's own idea of where a page begins
 * and is most of why the player reads as a Windows Phone one. With the words in the slot that same
 * 106dp is 70dp of blank page above a name, so the header is brought up to sit just under the clock
 * instead — cleared by the status bar's own **inset** rather than by a number, the lesson the
 * panorama's title already paid for, so that a hidden bar (full screen) or a cutout both come out
 * right without anything here knowing about them.
 */
private val LyricsHeaderGap = 16.dp

/**
 * How long the player takes to change face.
 *
 * It is a whole page rearranging itself — the cover leaves, the words come in over it, the position
 * bar and the name travel to where they now belong — so it is nearer the framework's own
 * `MetroSlideInMillis` than to a line of lyrics changing. Short of it all the same: this is one
 * screen turning into another, not a page being navigated to.
 */
private const val FaceSwapMillis = 420

/**
 * The text of the player, on the Windows Phone type ramp: the artist as the page's heading in the
 * light weight at the size of a WP8 page title, the album a step down in the regular weight, and the
 * song under the cover large and light again — the three things a now-playing screen is *for*, set
 * as the biggest type on it, the way the phone's own Music player set them.
 *
 * The artist does not ellipsise. Like a panorama title it runs off the right edge of the screen when
 * it is longer than the screen is wide — cut by the edge, not abbreviated — which is the Metro way
 * of setting a heading too big for its page.
 *
 * In the words mode the header steps back down to [ArtistLyricsSize] and [AlbumLyricsSize], in step
 * with the change of face: the header is not what is being read on that screen, and at 45sp it
 * crowded the words.
 */
private val ArtistSize = 46.sp
private val AlbumSize = 26.sp
private val TitleSize = 32.sp
private val ArtistLyricsSize = 30.sp
private val AlbumLyricsSize = 19.sp

/**
 * The header's change of size, as a *scale* of the cover-mode type rather than a font size.
 *
 * Animating `fontSize` re-lays the text out on every frame at a fractional size, and the font's
 * hinting snaps each of those sizes to its own glyph metrics: the name shimmers and steps instead of
 * shrinking. The lines are set once at [ArtistSize] and [AlbumSize] and drawn scaled, which is a
 * transform and moves smoothly — and because the height they take up is exactly that scale of the
 * full height, the words mode can work out in advance how much room the header gives back.
 */
private val ArtistLyricsScale = ArtistLyricsSize.value / ArtistSize.value
private val AlbumLyricsScale = AlbumLyricsSize.value / AlbumSize.value

/** The strip to the right of the cover that carries the toggles. */
private val ToggleColumnWidth = 68.dp

/**
 * How a track change plays out, taken frame by frame off a Lumia doing the same thing.
 *
 * The cover flies in from the right (`MetroSlideInMillis`) and the words turn over at the same time.
 *
 * The lead and the stagger are zero, and that is a deliberate departure from the phone. Measured off
 * a Lumia, the text there waits for the artwork to land and then turns over line by line; carried
 * over here, the owner read it as the name belonging to the *previous* track for a moment — the eye
 * takes cover and title as one label, and one half of a label changing before the other reads as a
 * glitch rather than as choreography. Kept as named constants rather than deleted so that the shape
 * is still expressible: raise them and the phone's sequence comes back.
 */
private const val TextLead = 0
private const val TextStagger = 0

/**
 * The full player.
 *
 * Reading order is the WP8 one — who and what above the cover, which track below it — with the
 * artwork sitting in the middle of the text rather than on top of it, and the toggles stacked down
 * the strip beside the cover so the position bar can hug its bottom edge. Only the transport gets
 * rings, because those are the buttons you hit without looking; it sits low, within thumb reach.
 * Nothing carries a caption — captions belong to the app bar, and the player doesn't use one.
 *
 * Dragging sideways moves the page at finger speed. Let go past a quarter of the width and it commits
 * to the next or previous track; anything less springs back. At the end of the queue there is nothing
 * to commit to, and the page says so by barely moving.
 *
 * The backdrop used to lag behind at a third of that, for the panorama's sense of depth. It cannot any
 * more: the cover is now drawn at exactly the screen's width so that the strip can show the top of the
 * same square, and at that scale there is no slack at the edges for anything to travel into.
 *
 * **The downward gesture is not this screen's any more.** Pushing the player away moves the whole
 * rising page, so the drag belongs to [rising] and the page itself is what follows the finger — see
 * [MetroRisingPageState]. This screen only lends its surface to the gesture, and all three things a
 * drag here can mean go through one detector so a diagonal thumb cannot start two of them.
 *
 * The third is [queue]: pulling the player up again, once it has nowhere further to rise, brings the
 * queue out over it. The same movement that opened the player, one page further on. There is no mark
 * for it on the page any more: the caret under the transport was taken out at the owner's request.
 */
@Composable
fun NowPlayingScreen(
    rising: MetroRisingPageState,
    queue: MetroRisingPageState,
    backdrop: Bitmap?,
    onNavigate: (Screen) -> Unit
) {
    val services = LocalServices.current
    val colors = MetroTheme.colors
    val state by services.player.state.collectAsStateWithLifecycle()
    val stats by services.stats.stats.collectAsStateWithLifecycle()
    val settings by services.settings.settings.collectAsStateWithLifecycle()

    if (!state.hasTrack) {
        Box(Modifier.fillMaxSize().background(colors.bg)) {
            MetroEmptyNote(stringResource(R.string.player_nothing))
        }
        return
    }

    val trackId = state.trackId ?: -1L

    // Where the artist and the album lines lead. Asked of the library rather than of the player's
    // state, because the library is what the pages are built from: a file opened from another app,
    // or an artist who has been hidden, has no page to go to, and its line is then just a line.
    val library by services.library.library.collectAsStateWithLifecycle()
    val track = library.track(trackId)
    val artistTarget = track?.artistId?.takeIf { library.tracksOfArtist(it).isNotEmpty() }
    val albumTarget = track?.albumId?.takeIf { library.album(it) != null }

    // What goes in the square: the cover, or the song's words following the music in its place.
    //
    // **The whole screen is laid out for the answer, so the answer has to be settled before it is
    // used** — hence [rememberPlayerWords] rather than a lookup inside the slot. A song with no
    // words at all gets the ordinary player back, which is the mode the setting is asking to be
    // departed from only where there is something to depart for.
    //
    // It decides the position tick too — words need asking four times a second, the hairline under
    // them is happy with two — and the flow is remembered rather than rebuilt, or every
    // recomposition would hand `collectAsStateWithLifecycle` a new flow and restart the poll it is
    // already running.
    val wordsChosen = settings.playerFace == PlayerFace.Lyrics
    val words = if (wordsChosen) rememberPlayerWords(trackId) else null
    val facingLyrics = words?.showWords == true

    // Whether this song has anything to switch *to*, for the toggle beside the cover. Asked of the
    // index, which is free; see [rememberWordsAvailable].
    val wordsAvailable = rememberWordsAvailable(trackId)
    val tickMs = if (facingLyrics) LyricTickMs else SliderTickMs
    val positions = remember(services, tickMs) { services.player.positionFlow(tickMs) }
    val position by positions.collectAsStateWithLifecycle(0L)

    // Sideways drags page between the tracks of the queue, and the neighbours are on screen the whole
    // time: what arrives is what was visibly coming, and letting go only finishes a movement already
    // made. The page it lands on is a queue position, so the swipe commits with `skipToQueueIndex`
    // rather than with "next" — it has shown you the face you are landing on, so it must land there.
    //
    // It replaces a swipe that moved the whole screen under the finger and then, on release, sprang
    // *back* to the middle while the content changed on its own schedule underneath: the finger went
    // one way, the page went the other, and the change was a third movement the hand had no part in.
    //
    // `slideEpoch` counts the changes this gesture caused. The face reads it as a `key`, so a track
    // that arrived by sliding does not *also* fade and fly its cover in — while a track that arrived
    // any other way (a button, the last one ending) keeps that choreography, which is what it is for.
    var slideEpoch by remember { mutableIntStateOf(0) }
    val pager = rememberMetroPageSwipe(
        index = state.queueIndex,
        previousIndex = state.previousIndex,
        nextIndex = state.nextIndex,
        onSettleTo = { index ->
            slideEpoch++
            services.player.skipToQueueIndex(index)
        }
    )

    // While the thumb is held, show where it is rather than where playback is.
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val playedFraction = when {
        scrubbing != null -> scrubbing!!
        state.durationMs > 0 -> (position.toFloat() / state.durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownPosition =
        if (scrubbing != null) (state.durationMs * playedFraction).toLong() else position

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            // One detector, both axes, the axis decided once per gesture — and the vertical one is
            // the page's own position rather than a nudge that triggers an animation afterwards.
            .metroRiseDrag(
                rising,
                pager,
                enabled = settings.gesturePlayerDown,
                swipeEnabled = settings.gesturePlayerSwipe,
                upward = queue,
                upwardEnabled = settings.gesturePlayerUp
            )
            // Inside the detector above, so it is asked first: a push that is mostly downward is
            // the player going away, even when the thumb's arc starts it off diagonally.
            .pushDownToCollapse(rising, enabled = settings.gesturePlayerDown)
    ) {
        // As wide as the gutter and the toggle strip leave it, and no taller than what the text
        // and the button bar don't need — on a short screen the chrome wins. The backdrop asks for
        // the same size, so the two of them draw one cached bitmap.
        // The box now runs edge to edge, so the gesture bar's height has to come out of the sum by
        // hand — the column that holds the chrome is inset by it.
        val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val coverSize = (maxHeight - ChromeHeight - navigationInset)
            .coerceIn(120.dp, maxWidth - 24.dp - ToggleColumnWidth)
        val loading = rememberAlbumArt(state.albumId, trackId, coverSize)

        // How far through the change of face we are: 0 the cover, 1 the words. One number drives all
        // of it — the slot's height, the header's inset and the two pictures sliding past each other
        // — which is what makes them read as one movement instead of four things starting together.
        val swap by animateFloatAsState(
            targetValue = if (facingLyrics) 1f else 0f,
            animationSpec = tween(FaceSwapMillis, easing = MetroSlideInEasing),
            label = "player-face"
        )

        val coverTopPad = 106.dp
        val wordsTopPad = statusInset + LyricsHeaderGap

        // **What the slot is worth in the words mode, learnt from the layout rather than worked out.**
        //
        // A weight cannot be animated — "whatever is left over" is not a number to travel towards — so
        // the height has to be stated, and the last time this file stated it from [ChromeHeight] it was
        // 80dp out, because that constant is a generous estimate rather than a measurement. So the
        // column keeps a weighted spacer under the title and *this reads it*: what the slot could have
        // is what it has now plus whatever that spacer is holding, less the gap the title keeps above
        // the transport, plus the inset the header gives back on the way into the words.
        //
        // Read only when nothing is moving, so the animation cannot chase its own target, and the two
        // resting states agree — measuring in the words mode gives the same number back, which is what
        // makes it stable rather than a guess that drifts.
        var facePx by remember { mutableIntStateOf(0) }
        var slackPx by remember { mutableIntStateOf(0) }
        // The artist and album lines as they stand, so that the room they give back by shrinking
        // into the words mode is part of the sum. Leaving it out is what made the bottom of the
        // page jump at the end of the change: the face was sized for a header that had not shrunk,
        // and the first measurement at rest found the difference and took it all in one frame.
        var artistPx by remember { mutableIntStateOf(0) }
        var albumPx by remember { mutableIntStateOf(0) }
        var wordsFace by remember { mutableStateOf(Dp.Unspecified) }
        val density = LocalDensity.current
        LaunchedEffect(swap, facePx, slackPx, artistPx, albumPx, coverSize, wordsTopPad) {
            if (facePx <= 0 || (swap != 0f && swap != 1f)) return@LaunchedEffect
            val reclaimed = if (swap == 0f) {
                // Rounded the way [scaledFromTopStart] rounds, so the prediction and the layout
                // land on the same pixel and the measurement at rest in the words mode agrees.
                val headerPx = artistPx - (artistPx * ArtistLyricsScale).roundToInt() +
                    albumPx - (albumPx * AlbumLyricsScale).roundToInt()
                coverTopPad - wordsTopPad + with(density) { headerPx.toDp() }
            } else {
                0.dp
            }
            wordsFace = with(density) { (facePx + slackPx).toDp() } - TransportGap + reclaimed
        }

        // Until that has been measured once, the two modes lay out exactly as they did before any of
        // this: a weight for the words, the square for the cover. A first switch without the travel is
        // better than a first switch to the wrong place.
        val measured = wordsFace != Dp.Unspecified
        val faceHeight = if (measured) lerp(coverSize, wordsFace, swap) else coverSize

        // The neighbours' covers, decoded before a finger asks for them. They are composed only while
        // a swipe is in flight, so without this the first frames of every drag would carry a blank
        // square in from the edge and fill it in a moment later — the same defect the strip's backdrop
        // had, for the same reason. Two extra decodes at the player's own size while the player is
        // open, which the artwork cache is capped for; at rest nothing here holds them.
        val coverPx = with(LocalDensity.current) { coverSize.roundToPx() }
        LaunchedEffect(state.previous?.albumId, state.next?.albumId, coverPx) {
            state.previous?.let { services.artwork.load(it.albumId, it.trackId, coverPx) }
            state.next?.let { services.artwork.load(it.albumId, it.trackId, coverPx) }
        }

        // The words in the square used to carry their own `metroRiseOverscroll`, so a song whose
        // lyrics fit was not a third of the player that could not be pushed away. The whole page has
        // one now (see `pushDownToCollapse` above) and the list hands its spare downward drag to that.
        // A second one in here would settle the page twice on every flick, since a fling is offered
        // to the outer connection first and this one would then settle again from no speed at all.

        // Layer 1 — the backdrop, overscaled so panning never exposes an edge, at a third of the
        // drag speed, and cross-fading from one album to the next with its own small parallax.
        //
        // [backdrop] is handed in by the shell and is the same object the strip is drawing. It used to
        // be remembered here, with the last decoded cover kept so a track change did not blink black —
        // which is still needed and now happens out there instead. In here it could not work: this page
        // does not exist while it rests in the strip, so that state was rebuilt from nothing on every
        // pull, the first frames of the gesture had no bitmap, and the cover arrived a moment later
        // through the cross-fade. On screen that is the backdrop changing as you drag.
        if (backdrop != null) {
            MetroCrossfade(target = backdrop, modifier = Modifier.fillMaxSize()) { artwork ->
                if (artwork != null) {
                    Image(
                        bitmap = artwork.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // The whole page — see the note on [BackdropAlpha] for what this was
                        // instead for a while and why it came back. `Crop` on a square in a tall box
                        // shows the cover's full *height* with its sides cut off, so the picture is
                        // a picture rather than a band with flat colour under it. The strip is
                        // handed this page's height and draws the same thing, which is the one
                        // property here that is not allowed to slip.
                        //
                        // No sideways parallax: the drag would expose an edge, and the vertical
                        // parallax went earlier, when the page itself started doing the whole travel.
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = BackdropAlpha }
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.bg.copy(alpha = BackdropScrimTop),
                                colors.bg.copy(alpha = BackdropScrimBottom)
                            )
                        )
                    )
            )
        }

        // Layer 2 — everything you actually read and touch, at full drag speed.
        //
        // The insets live here rather than on the whole screen: the backdrop above runs to all four
        // edges, and only the things you press are kept clear of the gesture bar.
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = lerp(coverTopPad, wordsTopPad, swap), bottom = 52.dp)
        ) {
            // The words wait for the artwork to land before they start turning over, then go in
            // sequence down the screen rather than all at once — both measured off the phone, and
            // together they are most of why a track change reads as slow and deliberate there. That
            // is for a change nobody dragged for; a slid one is already a movement, so the `key` on
            // `slideEpoch` rebuilds the swap and it starts on the new words instead of turning over.
            PagedFace(
                pager = pager,
                current = state.artist,
                previous = state.previous?.artist,
                next = state.next?.artist,
                // No end padding: the name runs to the edge of the screen and off it.
                modifier = Modifier
                    .padding(start = 24.dp)
                    .onSizeChanged { artistPx = it.height }
            ) { artist, slot ->
                val size = ArtistSize
                val scale = { 1f + (ArtistLyricsScale - 1f) * swap }
                if (slot == 0) {
                    FaceLink(artistTarget?.let { { onNavigate(Screen.ArtistDetail(it)) } }) {
                        key(slideEpoch) {
                            MetroSwap(target = artist, delayMillis = TextLead) {
                                FaceLine(it, size, colors.fg, light = true, bleed = true, scale = scale)
                            }
                        }
                    }
                } else {
                    FaceLine(artist, size, colors.fg, light = true, bleed = true, scale = scale)
                }
            }
            PagedFace(
                pager = pager,
                current = state.album,
                previous = state.previous?.album,
                next = state.next?.album,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .onSizeChanged { albumPx = it.height }
            ) { album, slot ->
                val size = AlbumSize
                val scale = { 1f + (AlbumLyricsScale - 1f) * swap }
                if (slot == 0) {
                    FaceLink(albumTarget?.let { { onNavigate(Screen.AlbumDetail(it)) } }) {
                        key(slideEpoch) {
                            MetroSwap(target = album, delayMillis = TextLead + TextStagger) {
                                FaceLine(it, size, colors.subtle, scale = scale)
                            }
                        }
                    }
                } else {
                    FaceLine(album, size, colors.subtle, scale = scale)
                }
            }

            Spacer(Modifier.height(10.dp))

            // **How tall the face is, which is the one thing the two modes do not agree about.**
            //
            // A cover is artwork and is square. The words are not, and on a phone that square is
            // limited by the *width* — it has to leave the toggle strip its 68dp — so on a tall
            // screen a third of the page went into the weighted spacer under the title and was
            // simply blank. In this mode the face takes it: a weight rather than the arithmetic,
            // because [ChromeHeight] is a stated constant and the column knows the real answer.
            // The position bar, the title and the toggles all travel down with the face, which is
            // how the title ends up against the transport — where it is read from anyway.
            Row(
                (if (measured) Modifier.height(faceHeight)
                else if (facingLyrics) Modifier.weight(1f)
                else Modifier.height(coverSize))
                    .onSizeChanged { facePx = it.height }
                    .padding(start = 24.dp)
            ) {
                Box(Modifier.width(coverSize).fillMaxHeight()) {
                    // No continuum key: the player is an overlay over the navigation host, not a
                    // page inside it, so there is no element on the page below for a shared
                    // element to pair with. `metroSlideIn` is keyed on the album, not the track,
                    // because skipping between tracks of one album leaves the same cover on screen —
                    // and it is skipped altogether for a cover that arrived by sliding, which has
                    // just travelled the width of the screen under the finger.
                    PagedFace(
                        pager = pager,
                        current = state.face,
                        previous = state.previous,
                        next = state.next,
                        modifier = Modifier.fillMaxHeight()
                    ) { face, slot ->
                        when {
                            // The neighbours either side of a swipe are covers whichever face this
                            // screen is wearing. Their words are not known — asking for them would
                            // be a lookup per drag, half of them in a direction the finger never
                            // commits to — and a record coming in under the thumb is how you see
                            // which record it is. The words take over once it has landed.
                            slot != 0 -> FaceCover(
                                albumId = face.albumId,
                                trackId = face.trackId,
                                width = coverSize
                            )
                            // **The two faces pass each other**, both moving down, with the slot
                            // clipping them: the words come in over the top edge and push the cover
                            // out of the bottom, and going back the cover comes up from underneath.
                            // A slide and not a cross-fade, because a fade through the page reads as
                            // two pictures being swapped while this is one thing replacing another.
                            //
                            // Translation and not layout: each face is laid out once at the slot's
                            // full size and *drawn* moving, so the list of words is not re-measured
                            // on any frame of this. The only thing that really changes size is the
                            // slot, and the column around it.
                            else -> Box(
                                Modifier.fillMaxSize().clipToBounds(),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                if (swap < 1f) {
                                    Box(
                                        Modifier
                                            // **Its own height, not the slot's.** `requiredHeight`
                                            // ignores the constraint the animating slot hands down,
                                            // so each face is measured once at the size it has when
                                            // it is the one being shown and is only *drawn* moving.
                                            // Without it the list of words is re-measured on every
                                            // frame of the change, which is the one thing this
                                            // screen has already been taught not to do.
                                            .requiredHeight(coverSize)
                                            .graphicsLayer { translationY = swap * size.height }
                                    ) {
                                        // No `metroSlideIn` while the faces are changing: the cover
                                        // is already travelling, and two movements at once on one
                                        // square is a shuffle rather than an arrival.
                                        key(slideEpoch) {
                                            FaceCover(
                                                albumId = face.albumId,
                                                trackId = face.trackId,
                                                width = coverSize,
                                                modifier = if (swap == 0f) {
                                                    Modifier.metroSlideIn(face.albumId)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                        }
                                    }
                                }
                                if (swap > 0f) {
                                    Box(
                                        Modifier
                                            .then(
                                                if (measured) Modifier.requiredHeight(wordsFace)
                                                else Modifier.fillMaxHeight()
                                            )
                                            .graphicsLayer {
                                                translationY = -(1f - swap) * size.height
                                            }
                                    ) {
                                        PlayerLyrics(
                                            lyrics = words?.lyrics,
                                            width = coverSize,
                                            listModifier = Modifier
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // The toggles stack down the strip beside the face, gathered at the bottom of it
                // rather than spread over the whole edge — as a cluster they read as one group of
                // switches instead of three unrelated marks. The row is exactly the face's height,
                // which is what keeps them level with its bottom edge in both modes; the position
                // bar sits *under* the row for the same reason.
                Column(
                    Modifier.width(ToggleColumnWidth).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
                ) {
                    // **The words are switched on from here, not from a settings page.** It is a
                    // way of looking at what is playing rather than a preference — it changes with
                    // the song, and the song is on this screen. The choice is still remembered
                    // (`Settings.playerFace`), so the player opens the way it was left.
                    //
                    // Lit by the *chosen* face rather than by what is on screen, because those come
                    // apart: a song with no words shows its cover whatever the mode is, and a button
                    // that went dark for it would read as the mode having switched itself off. It
                    // greys out only where there is nothing to switch to — a song the index has
                    // already written off, and only while the cover is what is being shown, so the
                    // mode can always be turned off again from wherever you are.
                    TransportButton(
                        icon = MetroIcon.LyricLines,
                        contentDescription = stringResource(
                            if (wordsChosen) R.string.player_face_cover else R.string.player_face_words
                        ),
                        iconSize = 23.dp,
                        touchSize = 52.dp,
                        enabled = wordsChosen || wordsAvailable,
                        active = wordsChosen
                    ) {
                        services.settings.setPlayerFace(
                            if (wordsChosen) PlayerFace.Cover else PlayerFace.Lyrics
                        )
                    }
                    val favorite = trackId in stats.favorites
                    TransportButton(
                        icon = if (favorite) MetroIcon.StarFilled else MetroIcon.Star,
                        contentDescription = stringResource(
                            if (favorite) R.string.menu_unfavorite else R.string.menu_favorite
                        ),
                        iconSize = 23.dp,
                        touchSize = 52.dp,
                        active = favorite
                    ) { services.stats.toggleFavorite(trackId) }
                    TransportButton(
                        icon = MetroIcon.Shuffle,
                        contentDescription = stringResource(
                            if (state.shuffle) R.string.player_shuffle_on else R.string.action_shuffle
                        ),
                        iconSize = 23.dp,
                        touchSize = 52.dp,
                        active = state.shuffle
                    ) { services.player.toggleShuffle() }
                    TransportButton(
                        icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                            MetroIcon.RepeatOne
                        } else {
                            MetroIcon.Repeat
                        },
                        contentDescription = stringResource(repeatLabel(state.repeatMode)),
                        iconSize = 23.dp,
                        touchSize = 52.dp,
                        active = state.repeatMode != Player.REPEAT_MODE_OFF
                    ) { services.player.cycleRepeat() }
                }
            }

            // A hairline against the bottom edge of the face, and the time the track has left
            // rather than a second copy of its duration.
            Column(Modifier.padding(start = 24.dp).width(coverSize)) {
                MetroSlider(
                    value = playedFraction,
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = {
                        scrubbing?.let { services.player.seekToFraction(it) }
                        scrubbing = null
                    },
                    trackHeight = 2.dp,
                    thumbSize = null,
                    height = 18.dp
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TimeLabel(formatDuration(shownPosition))
                    TimeLabel("-" + formatDuration(state.durationMs - shownPosition))
                }
            }

            Spacer(Modifier.height(6.dp))

            PagedFace(
                pager = pager,
                current = state.title,
                previous = state.previous?.title,
                next = state.next?.title,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) { title, slot ->
                if (slot == 0) {
                    key(slideEpoch) {
                        MetroSwap(target = title, delayMillis = TextLead + TextStagger * 2) {
                            FaceLine(it, TitleSize, colors.fg, light = true)
                        }
                    }
                } else {
                    FaceLine(title, TitleSize, colors.fg, light = true)
                }
            }

            // Whatever the face did not take, and **the one thing that says how much that is**: with
            // the slot's height stated rather than weighted, this is the slack, and reading it is how
            // the words' height above is learnt. In the cover mode it is most of the page under the
            // title; in the words mode it comes out at the [TransportGap] the title keeps above the
            // rings, which is the arithmetic agreeing with itself.
            if (measured) {
                Spacer(Modifier.weight(1f).onSizeChanged { slackPx = it.height })
            } else if (facingLyrics) {
                Spacer(Modifier.height(TransportGap).onSizeChanged { slackPx = it.height })
            } else {
                Spacer(Modifier.weight(1f).onSizeChanged { slackPx = it.height })
            }

            // Left-aligned on the text gutter with room to breathe between the rings, low enough
            // that a thumb reaches them without shifting grip.
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                TransportButton(
                    icon = MetroIcon.Previous,
                    contentDescription = stringResource(R.string.action_previous),
                    iconSize = 22.dp,
                    touchSize = 64.dp,
                    ringSize = 60.dp
                ) { services.player.previous() }
                TransportButton(
                    icon = if (state.isPlaying) MetroIcon.Pause else MetroIcon.Play,
                    contentDescription = stringResource(
                        if (state.isPlaying) R.string.action_pause else R.string.action_play
                    ),
                    iconSize = 22.dp,
                    touchSize = 64.dp,
                    ringSize = 60.dp
                ) { services.player.togglePlayPause() }
                TransportButton(
                    icon = MetroIcon.Next,
                    contentDescription = stringResource(R.string.action_next),
                    iconSize = 22.dp,
                    touchSize = 64.dp,
                    ringSize = 60.dp
                ) { services.player.next() }
            }
        }
    }
}

/**
 * One element of the track's face — a line of text, the cover — drawn for the current track and, while
 * a swipe is in flight, for the neighbours either side, each translated to its own slot.
 *
 * Only the *face* is built out of these. The slider, the times, the toggles and the transport are not:
 * they belong to the player rather than to the track, and a swipe that carried the play button off the
 * screen with the artwork would be moving the furniture to change a record. That is also why this is a
 * wrapper per element instead of one layer around everything — the face and the chrome are interleaved
 * down the same column, and the column's own order is what puts the slider against the cover's bottom
 * edge.
 *
 * The neighbours are composed only while the pager says so, because each of them decodes a cover at the
 * player's own size: three at once for the length of a gesture is worth it, three at rest is not.
 *
 * [content] is given the slot it is drawing so that the current one can keep the choreography a track
 * change has when nobody swiped for it, and the neighbours — which are already sliding — can do without.
 */
/**
 * A cover in the face's slot, sitting on the slot's **bottom** edge.
 *
 * In the cover mode the slot is exactly the cover and this is a wrapper around nothing. In the words
 * mode the slot is taller than it is wide, and a cover — a track that has no words, or a neighbour
 * sliding in under a swipe — is still square: aligning it to the bottom is what keeps the position
 * bar hugging the artwork's own edge and the toggles gathered beside it, which is the one
 * relationship this layout is built on. The room it leaves is above it, where the backdrop — the
 * same cover, at the screen's width — is already showing through.
 */
@Composable
private fun FaceCover(
    albumId: Long,
    trackId: Long,
    width: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        Modifier.width(width).fillMaxHeight(),
        contentAlignment = Alignment.BottomStart
    ) {
        AlbumArt(
            albumId = albumId,
            representativeTrackId = trackId,
            size = width,
            modifier = modifier
        )
    }
}

/**
 * The artist or album line of the current track as a way to its page. Only the words are the
 * target, not the width of the screen beside them, and with nothing to go to the line is inert.
 * A drag started on it still crosses the slop and belongs to the swipe or the dismiss around it.
 */
@Composable
private fun FaceLink(onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    Box(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) { content() }
}

/**
 * One line of the face, so the three slots of a [PagedFace] cannot drift apart in style.
 *
 * [light] sets it in the light weight, which is what Metro uses for type this large; [bleed] lets it
 * run off the edge of the screen instead of ending in an ellipsis, as a heading does. [scale] draws it
 * smaller than [size] without setting it again — read at layout time, so an animated scale costs a
 * transform per frame rather than a recomposition and a fresh text layout.
 */
@Composable
private fun FaceLine(
    text: String,
    size: TextUnit,
    color: Color,
    light: Boolean = false,
    bleed: Boolean = false,
    scale: () -> Float = { 1f }
) {
    Text(
        text = text,
        modifier = Modifier.scaledFromTopStart(scale),
        color = color,
        fontFamily = if (light) MetroLight else MetroRegular,
        fontSize = size,
        maxLines = 1,
        softWrap = !bleed,
        overflow = if (bleed) TextOverflow.Clip else TextOverflow.Ellipsis
    )
}

@Composable
private fun <T> PagedFace(
    pager: MetroPageSwipeState,
    current: T,
    previous: T?,
    next: T?,
    modifier: Modifier = Modifier,
    content: @Composable (value: T, slot: Int) -> Unit
) {
    Box(modifier) {
        if (pager.active) {
            previous?.let { value ->
                Box(Modifier.graphicsLayer { translationX = pager.offsetForSlot(-1) }) {
                    content(value, -1)
                }
            }
            next?.let { value ->
                Box(Modifier.graphicsLayer { translationX = pager.offsetForSlot(1) }) {
                    content(value, 1)
                }
            }
        }
        Box(Modifier.graphicsLayer { translationX = pager.offsetForSlot(0) }) {
            content(current, 0)
        }
    }
}

/**
 * Lays its content out at full size and draws it scaled from the top-start corner, taking up only the
 * scaled room. The width it is offered is divided by the scale first, so a line that is drawn smaller
 * also gets the room to say more before it ellipsises or meets the edge.
 */
private fun Modifier.scaledFromTopStart(scale: () -> Float): Modifier =
    layout { measurable, constraints ->
        val s = scale().coerceAtLeast(0.01f)
        val inner = Constraints(
            maxWidth = if (constraints.hasBoundedWidth) {
                (constraints.maxWidth / s).roundToInt()
            } else {
                Constraints.Infinity
            }
        )
        val placeable = measurable.measure(inner)
        val width = (placeable.width * s).roundToInt()
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable.height * s).roundToInt()
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            placeable.placeRelativeWithLayer(0, 0) {
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }

@Composable
private fun TimeLabel(text: String) {
    Text(
        text = text,
        color = MetroTheme.colors.subtle,
        fontFamily = MetroRegular,
        fontSize = 12.sp
    )
}

@StringRes
private fun repeatLabel(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_ALL -> R.string.player_repeat_all
    Player.REPEAT_MODE_ONE -> R.string.player_repeat_one
    else -> R.string.player_repeat_off
}
