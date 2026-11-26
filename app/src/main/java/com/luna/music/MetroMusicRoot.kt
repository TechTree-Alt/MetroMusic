package com.luna.music

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.luna.music.ui.components.TurnTotalMillis
import com.luna.music.ui.components.TurnstilePage
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import com.luna.music.ui.components.LocalBackdropPan
import com.luna.music.ui.components.BackdropPan
import com.luna.music.ui.components.HomeAppBar
import com.luna.music.ui.components.AppBarDismissal
import com.luna.music.ui.components.HomeAppBarRestingHeight
import com.luna.music.ui.components.LocalAppBarDismissal
import com.luna.music.ui.components.dismissesOpenAppBar
import com.luna.music.ui.components.LocalHomeBarSpace
import com.metrocompose.MetroBarRiseMillis
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrocompose.MetroBackdrop
import com.metrocompose.MetroBottomBar
import com.metrocompose.MetroNavHost
import com.metrocompose.MetroRegular
import com.metrocompose.MetroRisingPage
import com.metrocompose.MetroTheme
import com.luna.music.ui.components.DropBanner
import com.metrocompose.MetroLight
import com.metrocompose.MetroProgressBar
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.metrocompose.rememberMetroBackStack
import com.metrocompose.rememberMetroRisingPage
import com.luna.music.R
import com.luna.music.core.LocalServices
import com.luna.music.data.playlist.PlaylistProblem
import com.luna.music.ui.components.AppBackdrop
import com.luna.music.ui.components.LocalOpenLyrics
import com.luna.music.ui.components.LocalOpenPlayer
import com.luna.music.ui.components.MiniPlayer
import com.luna.music.ui.components.MiniPlayerHeight
import com.luna.music.ui.components.rememberPlayingBackdrop
import com.luna.music.ui.formatTrackCount
import com.luna.music.ui.nav.Screen
import com.luna.music.playback.QueueNotice
import com.luna.music.ui.screens.AddToPlaylistScreen
import com.luna.music.ui.screens.AlbumDetailScreen
import com.luna.music.ui.screens.AlbumEditScreen
import com.luna.music.ui.screens.ArtistDetailScreen
import com.luna.music.ui.screens.CollectionScreen
import com.luna.music.ui.screens.GenreDetailScreen
import com.luna.music.ui.screens.LyricsScreen
import com.luna.music.ui.screens.NowPlayingScreen
import com.luna.music.ui.screens.PlaylistDetailScreen
import com.luna.music.ui.screens.PlaylistsScreen
import com.luna.music.ui.screens.QueueScreen
import com.luna.music.ui.screens.SearchScreen
import com.luna.music.ui.screens.SettingsDetailScreen
import com.luna.music.ui.screens.SettingsScreen
import kotlinx.coroutines.flow.first

/**
 * The app's shell: a back stack, the turnstile between destinations, the mini player pinned
 * underneath, the full player over the top of all of it, and the volume banner over even that.
 *
 * Adding a screen is a `Screen` entry plus a branch here — the `when` is exhaustive, so the
 * compiler will point at this spot until the new destination is handled.
 *
 * The player is the exception: it is not a destination but an overlay, opened by [LocalOpenPlayer]
 * and closed by Back. It has to be, for two reasons. It comes up out of the mini player rather than
 * swinging in from the side, and it is opened from anywhere — a list, the strip — so making it a
 * page would mean pushing it on top of whatever you were looking at and then tearing that page
 * down. Tearing it down is what made closing the player stutter: coming back re-composed a whole
 * panorama of lists in the frames the animation needed. As an overlay, nothing underneath moves.
 */
@Composable
fun MetroMusicRoot(
    initialScreen: Screen = Screen.Collection,
    openPlayerAtStart: Boolean = false,
    openPlayerRequests: Int = 0
) {
    val services = LocalServices.current
    val nav = rememberMetroBackStack(
        initial = initialScreen,
        save = { Screen.encode(it) },
        restore = { Screen.decode(it) }
    )
    val playerState by services.player.state.collectAsStateWithLifecycle()
    val volume by services.volume.state.collectAsStateWithLifecycle()
    var playerOpen by rememberSaveable { mutableStateOf(openPlayerAtStart) }

    // The window's own height, measured on the box the player fills rather than asked of the
    // configuration — `screenHeightDp` is the space an app is given and is short by the system bars,
    // while the rising player is full-bleed. The strip draws the top of the same picture the page
    // draws, so being 8% out is visible there.
    //
    // Kept *here*, at the top of the composable, and deliberately not read from a `BoxWithConstraints`
    // around the page. That was the first version and it strands the player: a subcomposition is
    // disposed and re-run when its constraints change, which happens while the insets settle at
    // startup, and it takes the page's `rememberCoroutineScope` with it — so a close animation running
    // at that moment simply dies, leaving the page a tenth of the way up the screen with nothing to
    // finish it. It needs the player to have been open when the process was killed, which is exactly
    // the state a restored session comes back in.
    var windowHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // The same height, in the form the strip's backdrop wants it: it draws the top band of exactly
    // what the page draws, so it needs a number on the very first frame too, before the box above
    // has been measured. The configuration's idea of the window is short by the system bars and is
    // close enough for one frame of one band.
    val backdropHeight =
        if (windowHeightPx > 0) with(density) { windowHeightPx.toDp() }
        else LocalConfiguration.current.screenHeightDp.dp

    // The playing cover, decoded once and held *here* so it outlives the player: the page is removed
    // from the composition while it rests in the strip, so a bitmap remembered inside it is rebuilt
    // from nothing on every pull and the first frames of the gesture have no artwork at all. Both the
    // strip's backdrop and the page's are handed this same object, which is also what stops them being
    // two decodes at two sharpnesses.
    val backdropArt = rememberPlayingBackdrop(playerState.albumId, playerState.trackId ?: -1L)

    // The player's position between the strip and the whole screen, which a finger can hold anywhere.
    // `playerOpen` stays the app's truth — a tap, Back, the widget all set it and the page follows —
    // and a drag reports back through `onOpenChange`, so the two never disagree about what is open.
    // The home screen's application bar sits *under* the strip, at the very bottom, where WP8 kept a
    // page's commands. So on the home screen the strip is that much higher, and the player has to rise
    // out of the strip's real top edge: the bar's height (its content, not the gesture bar under it,
    // which the rising page adds by itself) goes into the height the page rests at.
    //
    // **It never changes the height of the page area.** Every page is laid out at the same height
    // whatever screen it is: the bar is drawn *over* the bottom of the screen rather than taking a
    // place in the column, the strip is drawn raised by the bar's height while the bar is there, and
    // the home screen keeps that much room clear at its own bottom ([LocalHomeBarSpace]).
    //
    // It used to take a place in the column, and the page area above it changed height whenever it
    // came or went. Growing, that re-laid out the panorama on every frame of the page turn; taking its
    // space in one step instead, the navigation host's own size animation sprang the page frame from
    // the old height to the new one, drawing the page shifted up and settling back down before the
    // turn began — the jump on Back. Nothing about the page area moves now, so neither can happen.
    // The bar and the strip slide into and out of place together ([appBarShown]).
    val onHome = nav.current == Screen.Collection
    val navigationInsetPx =
        with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().roundToPx() }
    // The bar's height at rest, which is a constant: the bar is minimized there (see HomeAppBar), and
    // opening it slides it up *over* the strip rather than pushing the strip and the page up with it,
    // as an open application bar covered the page on the phone. Measuring the open bar instead would
    // re-lay out the whole panorama on every frame of the bar opening.
    val appBarPx = with(density) { HomeAppBarRestingHeight.roundToPx() }
    var appBarOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(onHome) { if (!onHome) appBarOpen = false }
    val appBarShown by animateFloatAsState(
        targetValue = if (onHome) 1f else 0f,
        animationSpec = tween(MetroBarRiseMillis),
        label = "app bar"
    )
    val appBarPresent = onHome || appBarShown > 0f
    val appBarHeight = if (appBarPresent) with(density) { appBarPx.toDp() } else 0.dp
    // What the home screen keeps clear under its sections: the bar, and the gesture bar under it as
    // well when there is no strip — the strip, when there is one, answers for the gesture bar itself.
    val homeBarSpace = with(density) {
        (appBarPx + if (playerState.hasTrack) 0 else navigationInsetPx).toDp()
    }
    val backdropPan = remember { BackdropPan() }

    val rising = rememberMetroRisingPage(
        open = playerOpen,
        fromHeight = MiniPlayerHeight + appBarHeight,
        windowHeight = if (windowHeightPx > 0) with(density) { windowHeightPx.toDp() } else Dp.Unspecified,
        onOpenChange = { playerOpen = it }
    )

    // And the queue over the player, which is the same movement one page further on: the player comes
    // out of the strip, the queue comes out of the player. `fromHeight` of zero because it rises out of
    // the bottom edge of the screen rather than out of a strip — there is nothing of it showing while it
    // is shut, and nothing whose navigation inset it should inherit.
    //
    // A page rather than a destination for the same reason the player is one, and more so: a destination
    // would appear *under* the player, which is the one thing it must not do.
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val queueRising = rememberMetroRisingPage(
        open = queueOpen,
        fromHeight = 0.dp,
        windowHeight = if (windowHeightPx > 0) with(density) { windowHeightPx.toDp() } else Dp.Unspecified,
        onOpenChange = { queueOpen = it }
    )

    // A page reached from the player's artist or album line is a step *out of* the player, so Back
    // from it goes back into the player rather than to whatever was under it. This is the depth of
    // the stack that page sits at: Back there pops it and raises the player again. Pages pushed on
    // top of it unwind as usual and arrive back at it; anything that takes the stack below it, or
    // opening the player some other way, means that step is over and the marker goes.
    var returnToPlayerAt by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(nav.size) { if (nav.size < returnToPlayerAt) returnToPlayerAt = 0 }
    LaunchedEffect(playerOpen) { if (playerOpen) returnToPlayerAt = 0 }

    // An intent that reached an activity already on screen and asked for the player: a file opened
    // from a file manager, the home-screen tile. The activity counts them and the page still belongs
    // to this composable, so there is one owner of `playerOpen` and not two.
    LaunchedEffect(openPlayerRequests) {
        if (openPlayerRequests > 0) playerOpen = true
    }

    // Permission is granted by the time this composes, so it's safe to touch the library.
    LaunchedEffect(Unit) {
        services.library.start()
        services.player.connect()
        // Scrobbling watches the same state flow the UI does rather than polling the player.
        services.scrobbler.attach(services.player.state)
        services.scrobbler.flush()
        // Favourites against Last.fm's loves, if that is switched on. It waits for the library itself
        // and does nothing at all while the switch is off, so attaching unconditionally is right.
        services.loves.attach()
    }

    // The queue from the last time the app was open. It waits for the library rather than for the
    // scan flag: the queue is stored as track ids and there is nothing to resolve them against
    // until tracks exist, and a library that never fills has nothing to restore anyway.
    LaunchedEffect(Unit) {
        val scanned = services.library.library.first { it.tracks.isNotEmpty() }
        services.player.restoreLastSession(scanned)
    }

    // Which songs have lyrics is settled in the background, once per library, so the context menu
    // can answer without going to the network while it is unfolding.
    val library by services.library.library.collectAsStateWithLifecycle()
    LaunchedEffect(library.tracks.size) {
        if (library.tracks.isNotEmpty()) services.lyrics.probe(library.tracks)
        // The playlists are files full of paths and the ids those resolve to are MediaStore's, so a
        // scan that hands out new ones leaves every line pointing at nothing until they are read
        // again. This is also what picks up a `.m3u` dropped into the folder from somewhere else.
        services.playlists.refresh()
    }

    // Editing a playlist file another app owns needs the user to say so once. The store hands the
    // system dialog up here rather than to a screen, because the edit can be started from the
    // playlists list, from one playlist's page or from a long-press menu three screens away — and
    // the answer has to reach the operation that was refused wherever it came from.
    val playlistConsent by services.playlists.consent.collectAsStateWithLifecycle()
    val playlistConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> services.playlists.consentAnswered(result.resultCode == Activity.RESULT_OK) }
    LaunchedEffect(playlistConsent) {
        val request = playlistConsent ?: return@LaunchedEffect
        runCatching {
            playlistConsentLauncher.launch(IntentSenderRequest.Builder(request).build())
        }.onFailure { services.playlists.consentAnswered(granted = false) }
    }

    // Nothing to show the player about, so don't let it linger — losing the queue while it is
    // open (the last track ends, the service is stopped) should put you back on the library. The queue
    // screen goes with it: taking the last row out of it is a way to reach exactly that state.
    //
    // Only once something has actually played, which is not a detail: for the first second of a launch
    // there is no track *yet* — the controller is still connecting, and a queue being restored or a
    // file being opened from another app arrives later still. Closing on that shut the player the
    // moment it was asked for, so a song opened from a file manager played behind the library.
    var everHadTrack by remember { mutableStateOf(false) }
    LaunchedEffect(playerState.hasTrack) {
        if (playerState.hasTrack) {
            everHadTrack = true
        } else if (everHadTrack) {
            queueOpen = false
            playerOpen = false
        }
    }

    // One backdrop for the whole app: pages inside a MetroBackdrop leave their own background off, so
    // the gradient (or the cover, if settings say so) stays put while pages come and go over it
    // instead of the panorama having a wallpaper and every detail page dropping to flat black.
    // An open application bar closes on a tap anywhere outside it (see ApplicationBar); this is
    // the one place that hears every tap in the window.
    val appBarDismissal = remember { AppBarDismissal() }
    CompositionLocalProvider(
        LocalBackdropPan provides backdropPan,
        LocalAppBarDismissal provides appBarDismissal
    ) {
    MetroBackdrop(backdrop = { AppBackdrop() }) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { windowHeightPx = it.height }
                .dismissesOpenAppBar(appBarDismissal)
        ) {
        // No inset here on purpose: every page fills the window and the framework's own
        // components — MetroPage, MetroPanorama, MetroBottomBar — keep their *content* clear of the
        // gesture bar while their surfaces reach the bottom edge. Padding the whole column instead
        // is what left a strip of bare background under the panorama's backdrop.
        Column(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalOpenPlayer provides { playerOpen = true },
                LocalOpenLyrics provides { trackId -> nav.push(Screen.Lyrics(trackId)) },
                LocalHomeBarSpace provides homeBarSpace
            ) {
                // The Windows Phone turnstile (see TurnstilePage): the old page turns away about its
                // left edge, then the new one swings in. The host's own transition is only asked to
                // keep both pages composed for the length of the turn — fades that start and end
                // fully opaque — and each page draws its own turn from its enter/exit state. The
                // size animation is the framework's default, left alone: replacing it made Back
                // flash the page being returned to for a frame.
                MetroNavHost(
                    nav,
                    Modifier.weight(1f),
                    transition = { _ ->
                        fadeIn(tween(TurnTotalMillis), initialAlpha = 1f) togetherWith
                            fadeOut(tween(TurnTotalMillis), targetAlpha = 1f)
                    }
                ) { screen ->
                  // Settings pages bring their own entrance — their header and rows feather in —
                  // so they are shown, not swung, once the old page has turned away.
                  TurnstilePage(popping = nav.isPopping, turnsIn = screen !is Screen.SettingsDetail) {
                    when (screen) {
                        Screen.Collection -> CollectionScreen(onNavigate = nav::push)
                        Screen.Playlists -> PlaylistsScreen(onNavigate = nav::push)
                        Screen.Search -> SearchScreen(onNavigate = nav::push)
                        Screen.Settings -> SettingsScreen(onNavigate = nav::push)
                        is Screen.SettingsDetail -> SettingsDetailScreen(screen.page)
                        is Screen.AlbumDetail ->
                            AlbumDetailScreen(screen.albumId, onNavigate = nav::push)
                        is Screen.AlbumEdit ->
                            AlbumEditScreen(screen.albumId, onDone = { nav.pop() })
                        is Screen.ArtistDetail ->
                            ArtistDetailScreen(screen.artistId, onNavigate = nav::push)
                        is Screen.GenreDetail -> GenreDetailScreen(screen.genre)
                        is Screen.PlaylistDetail ->
                            PlaylistDetailScreen(screen.playlistId, onNavigate = nav::push)
                        is Screen.PlaylistAdd ->
                            AddToPlaylistScreen(screen.playlistId, onDone = { nav.pop() })
                        is Screen.Lyrics -> LyricsScreen(screen.trackId)
                    }
                  }
                }
            }

            // Tied to whether there is a track, and *not* to whether the player is open.
            //
            // It used to be both, and that is what made opening and closing the player feel ragged:
            // hiding the strip animates its height, which re-measures the page above it — a panorama
            // of lists — on every frame of the rise, and the strip the page is supposed to drop back
            // into was shrinking while the page slid towards where it used to be. The player covers
            // the strip completely for the whole animation anyway, so there is nothing to hide.
            // The cover goes behind the strip, and behind the navigation bar under it, which is why
            // it is the *bar's* background and not something the strip draws inside itself. Without
            // it the player's full-bleed artwork ends its drop by turning into a black rectangle.
            MetroBottomBar(
                visible = playerState.hasTrack,
                // Raised by the bar's height while the bar is there, so it sits on top of it.
                modifier = Modifier.graphicsLayer {
                    translationY = -appBarShown * appBarPx
                },
                // Solid, in the theme's background: white in the light theme, black in the dark one,
                // and the user's own colour when one is chosen in Settings → theme. It used to show the
                // top band of the playing cover, to match the player rising out of it; the player's
                // last stretch is a cross-fade with the strip now (StripHandOff), so they need not match.
                background = {
                    Box(Modifier.fillMaxSize().background(MetroTheme.colors.bg))
                }
            ) {
                MiniPlayer(state = playerState, rising = rising, onOpen = { playerOpen = true })
            }
        }

        // The home screen's application bar, over the bottom of the screen, under the strip — which
        // is drawn raised by its height. It belongs to the home screen alone, so it slides away as a
        // page is opened over it and slides back on the way home.
        //
        // The player rises out of the strip above it and covers it on the way up; on the way back
        // down the bar comes back with the strip, fading in as it rises into place over the last
        // stretch of the drop — the same hand-off as the strip's (see StripHandOff).
        //
        // Searching the whole library is its one command, one tap from every section, and it does not
        // compete with the section headers, whose tap filters that section only.
        if (appBarPresent) {
            HomeAppBar(
                onSearch = { nav.push(Screen.Search) },
                onSettings = { nav.push(Screen.Settings) },
                insetNavigationBar = true,
                expanded = appBarOpen,
                onExpandedChange = { appBarOpen = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val covered = (rising.progress / StripHandOff).coerceIn(0f, 1f)
                        alpha = (1f - covered) * appBarShown
                        // Below the bottom edge by its whole height when not shown, and the player
                        // hand-off's small rise on top of that.
                        translationY = (1f - appBarShown) * size.height + covered * AppBarRise.toPx()
                    }
            )
        }

        // Full bleed: the player's artwork backdrop reaches the bottom edge of the screen rather
        // than stopping at the gesture bar and leaving a strip of app background — which is exactly
        // what it looked like on a phone. The screen insets its own controls instead, and
        // MetroRisingPage adds the navigation bar to the strip height it rises out of.
            MetroRisingPage(rising) {
                // The last stretch of the drop is a cross-fade with the strip rather than the page
                // covering it to the very end: settling, the page spends its final moments sitting
                // exactly over the strip, showing the top of its own picture where the strip's words
                // belong, so the strip only appeared once the movement had quite finished. Fading
                // the page out over the last [StripHandOff] of its travel lets the strip — which
                // rises into place over the same stretch, see MiniPlayer — be there as it lands.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (rising.progress / StripHandOff).coerceIn(0f, 1f) }
                ) {
                NowPlayingScreen(
                    rising = rising,
                    queue = queueRising,
                    backdrop = backdropArt,
                    // The page is pushed under the player as it drops, so it is already there when
                    // the player is gone — and not pushed a second time if it is already on top.
                    onNavigate = { screen ->
                        playerOpen = false
                        if (nav.current != screen) nav.push(screen)
                        returnToPlayerAt = nav.size
                    }
                )
                }
            }

            // Over the player, and composed after it so that it is: the queue is the only thing in the
            // app that covers the player, and it is opaque while it does.
            MetroRisingPage(queueRising) {
                QueueScreen(rising = queueRising, onClose = { queueOpen = false })
            }
        }
    }
    }

    // The volume keys are consumed by the activity so Android's own panel never appears; this is
    // what appears instead. Built here rather than with the framework's MetroVolumeBanner so it can
    // be just the caption, the level and the bar: no speaker glyph, and no now-playing row — the
    // banner arrives because a volume key was pressed, and the strip and the player already say
    // what is playing. The caption is set exactly like the number — lowercase, Light, the same
    // size, no tracking — and shares its baseline, so the two read as one line across the strip.
    DropBanner(
        visible = volume.showing,
        onHide = { services.volume.hide() },
        resetKey = volume.step
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.volume),
                color = MetroTheme.colors.fg,
                fontFamily = MetroLight,
                fontSize = 30.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
            )
            Text(
                text = volume.step.toString(),
                color = MetroTheme.colors.fg,
                fontFamily = MetroLight,
                fontSize = 30.sp,
                modifier = Modifier.alignByBaseline()
            )
        }
        Spacer(Modifier.height(10.dp))
        MetroProgressBar(progress = volume.level.coerceIn(0f, 1f))
    }

    // "Play next" has nothing on screen to show for itself — the page you are on does not change and
    // neither does the song — so it says so here. Which matters most when queueing several albums in
    // a row, where the only other feedback is three minutes of waiting.
    //
    // Suppressed while the volume banner is up: they are both the same strip in the same place, and
    // the volume one is answering a key that is being held right now.
    var queued by remember { mutableStateOf<QueueNotice?>(null) }
    LaunchedEffect(Unit) {
        services.player.queued.collect { queued = it }
    }
    val notice = queued
    DropBanner(
        visible = notice != null && !volume.showing,
        onHide = { queued = null },
        resetKey = notice?.sequence,
        lingerMillis = QueuedHintMillis
    ) {
        Text(
            text = stringResource(R.string.queued_heading),
            color = MetroTheme.colors.subtle,
            fontFamily = MetroRegular,
            fontSize = 15.sp
        )
        Text(
            text = notice?.label.orEmpty(),
            color = MetroTheme.colors.fg,
            fontFamily = MetroRegular,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (notice != null && notice.count > 1) {
            Text(
                text = formatTrackCount(notice.count),
                color = MetroTheme.colors.subtle,
                fontFamily = MetroRegular,
                fontSize = 15.sp
            )
        }
    }

    // And when the filesystem refuses something outright — a folder that cannot be written, a file
    // that will not parse — the same strip says so. A playlist edit has nothing else on screen to
    // report with: the list has already changed, because the change is real in memory and only the
    // file is behind.
    val playlistProblem by services.playlists.problem.collectAsStateWithLifecycle()
    DropBanner(
        visible = playlistProblem != null && notice == null && !volume.showing,
        onHide = { services.playlists.problemSeen() },
        resetKey = playlistProblem,
        lingerMillis = ProblemHintMillis
    ) {
        Text(
            text = stringResource(
                when (playlistProblem) {
                    PlaylistProblem.Import -> R.string.playlist_problem_import
                    PlaylistProblem.Export -> R.string.playlist_problem_export
                    else -> R.string.playlist_problem_save
                }
            ),
            color = MetroTheme.colors.fg,
            fontFamily = MetroRegular,
            fontSize = 16.sp
        )
    }

    // Composed after the nav host, so while the player is open this takes Back first and the
    // back stack underneath keeps its place.
    // After the nav host's own handler, so this one wins on the page the player led to.
    BackHandler(enabled = !playerOpen && returnToPlayerAt > 0 && nav.size == returnToPlayerAt) {
        returnToPlayerAt = 0
        nav.pop()
        playerOpen = true
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }

    // And after that one, so Back closes the queue and leaves the player it was over. The two are a
    // stack of overlays and Back unwinds them one at a time, which is what the back stack under them
    // does as well.
    BackHandler(enabled = queueOpen) { queueOpen = false }

    // Leaving takes two presses. At the root of the stack a single stray Back — and the gesture is
    // easy to trigger by accident on a tall phone — would otherwise close a music app mid-song.
    //
    // The second press is not handled here at all: arming the guard *disables* this handler, so Back
    // falls through to the system and closes the activity the way it normally would. No `finish()`,
    // and nothing to get wrong about which press was which.
    var leaving by remember { mutableStateOf(false) }
    // Not while the home app bar is open: Back closes that first (see ApplicationBar).
    BackHandler(enabled = !playerOpen && !nav.canGoBack && !leaving && !appBarOpen) { leaving = true }
    DropBanner(visible = leaving, onHide = { leaving = false }, lingerMillis = LeaveHintMillis) {
        Text(
            text = stringResource(R.string.press_back_again),
            color = MetroTheme.colors.fg,
            fontFamily = MetroRegular,
            fontSize = 16.sp
        )
    }
}

/** How far the app bar rises into place as the player hands back to it, as the strip does. */
private val AppBarRise = 16.dp

/** How much of the player's travel, at the strip end, is a cross-fade between the page and the strip. */
const val StripHandOff = 0.1f

/** How long the "press back again" hint stays up, and therefore the window for that press. */
private const val LeaveHintMillis = 2200

/** Shorter than that: it is a receipt, not a question, and the next album is one tap away. */
private const val QueuedHintMillis = 1600

/** Longer than a receipt: this one is bad news and is worth reading. */
private const val ProblemHintMillis = 3000
