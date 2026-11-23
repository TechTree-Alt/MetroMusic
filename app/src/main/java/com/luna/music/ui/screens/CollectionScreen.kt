package com.luna.music.ui.screens

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import com.luna.music.ui.components.LocalHomeBarSpace
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import com.metrocompose.MetroSemilight
import com.metrocompose.LocalMetroColors
import com.luna.music.ui.components.LocalBackdropPan
import com.metrocompose.MetroTheme
import com.metrocompose.MetroLight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrocompose.ListRow
import com.metrocompose.MetroBottomInset
import com.metrocompose.MetroEmptyNote
import com.metrocompose.MetroPanorama
import com.metrocompose.MetroPanoramaSection
import com.metrocompose.MetroProgressDots
import com.luna.music.R
import com.luna.music.core.LocalServices
import com.luna.music.data.model.Album
import com.luna.music.data.store.LibrarySection
import com.luna.music.ui.components.PlaylistPickerHost
import com.luna.music.ui.components.TrackActionsHost
import com.luna.music.ui.components.WideTile
import com.luna.music.ui.components.rememberPlaylistPicker
import com.luna.music.ui.components.rememberTrackActions
import com.luna.music.ui.nav.Screen
import com.luna.music.ui.nav.SettingsPage

/**
 * The home page, and the whole library on it: a WP8 panorama whose sections *are* the ways of
 * slicing the collection rather than a menu that leads to them.
 *
 * That is the point of the arrangement — artists, albums and songs are one swipe from launch, not
 * one tap and then a swipe. The panorama is circular, so the *last* section is also one swipe to the
 * left of the first; settings live there, which is how they are always a single gesture away without
 * taking a place among the things you actually came for.
 *
 * Order comes from settings, so the user can put what they use first. Only the sections near the
 * viewport are composed, so it costs a screenful however many there are.
 */
@Composable
fun CollectionScreen(onNavigate: (Screen) -> Unit) {
    val services = LocalServices.current
    val library by services.library.library.collectAsStateWithLifecycle()
    val stats by services.stats.stats.collectAsStateWithLifecycle()
    val playlists by services.playlists.playlists.collectAsStateWithLifecycle()
    val playerState by services.player.state.collectAsStateWithLifecycle()
    val settings by services.settings.settings.collectAsStateWithLifecycle()
    val scanning by services.library.scanning.collectAsStateWithLifecycle()
    val loaded by services.library.loaded.collectAsStateWithLifecycle()
    val actions = rememberTrackActions()

    // One panel for the whole panorama rather than one per section: a section is a column inside a
    // scrolling row, which is no place to put a modal, and only one of them can be open anyway.
    val picker = rememberPlaylistPicker()

    // Which section's header was tapped, and what has been typed into it. One query at a time: the
    // box belongs to a section, and swiping away from that section puts it away with its filter.
    var searchIn by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    fun toggleSearch(section: LibrarySection) {
        searchIn = if (searchIn == section.name) null else section.name
        query = ""
    }
    fun searchFor(section: LibrarySection): String? = query.takeIf { searchIn == section.name }

    // Going somewhere puts the search away. Coming back to a panorama still holding the box, the
    // keyboard and a filtered list is coming back to unfinished business the user has already left.
    fun leave(screen: Screen) {
        searchIn = null
        query = ""
        onNavigate(screen)
    }

    val recentAlbums = remember(library, stats.recentAlbumIds) {
        stats.recentAlbumIds.mapNotNull(library::album)
    }

    // Genres need API 30+ to exist at all, so below that the section is left out. It is *not* left
    // out for a library that simply has no genre tags: the panorama reads its page numbers modulo the
    // section count, so a section appearing once the scan finishes would renumber every page and jump
    // the user somewhere else. Deciding on the API level keeps the count fixed for the whole session.
    val sections = remember(settings.sectionOrder) {
        settings.sections.filterNot {
            it == LibrarySection.Genres && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        }
    }

    // How far round the panorama is, in px within one lap, read off where its sections are on screen —
    // the panorama keeps its own scroll position to itself. The backdrop's pan, the title and the
    // section headers are all driven by it; see [coverPan].
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val sectionPadPx = with(density) { PanoramaSectionPadding.toPx() }
    val sectionPx = (widthPx - sectionPadPx).coerceAtLeast(1f)
    val lapPx = sectionPx * sections.size
    var travelled by remember { mutableFloatStateOf(0f) }
    // How far the title has rolled up, read the same way: the sections rise by exactly as much as
    // the title gives up (see `collapsingTitle`), so their top edge, against where it rests, is it.
    var sectionTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var sectionRestPx by remember { mutableFloatStateOf(Float.NaN) }

    val backdropPan = LocalBackdropPan.current
    // The real palette, handed back to the sections' own content below; the panorama itself is given
    // one whose foreground is invisible — see the note on HomeHeaders for why.
    val realColors = MetroTheme.colors
    val sectionTitles = sections.map { stringResource(sectionTitleOf(it)) }

    // **Measured against this page, never against the window.** A page coming back into view arrives
    // through the turnstile — scaled up from 92% and slid in from the side — and a position read in
    // window coordinates has that turn in it. Read that way, the sections seemed to start lower and to
    // the side and then rise into place, so the title took the rise for being rolled away and slid up,
    // and the backdrop's pan swung while the page settled: the whole screen jumped on the way back
    // from a settings page. Relative to the page's own root the turn cancels out; only the panorama's
    // own movements — its scroll, its title rolling away — are left.
    //
    // Held in a plain box rather than in state: it is written during placement (`onPlaced`), which
    // happens before any section reports where it is, and nothing needs to recompose when it changes.
    val page = remember { PageCoordinates() }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width }
            .onPlaced { page.coordinates = it }
    ) {
        Column(Modifier.fillMaxSize()) {
          CompositionLocalProvider(
              LocalMetroColors provides realColors.copy(fg = Color.Transparent)
          ) {
            MetroPanorama(
                // The application bar under the page takes the gesture bar's inset, so the lists in
                // the sections end above it rather than leaving the inset's height of room inside.
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.navigationBars),
                // "music" as the big title, and no overline above it — the hub's name, as the phone's
                // own music hub had it, rather than the app's. A second line over it would cost height
                // on the one screen where height is the scarce thing.
                // The panorama's own title is left empty and drawn by [HomeTitle] instead, once. The
                // panorama repeats its title along the loop so that going round is seamless, and a
                // short word like "music" came round three times; its only control, the speed,
                // makes the copies overlap when turned down. Empty, it still keeps the title's
                // height, so everything under it sits exactly where it did.
                title = "",
                overline = null,
                // More room over the title than the framework's 6dp: letters that rise above the
                // capitals — the dot of the i in "music" — were cut off by the edge it clips at.
                titleTopPadding = PanoramaTitleTopPadding,
                // No background of its own: the shell's one copy shows through, and this page pans it
                // (LocalBackdropPan). A copy drawn here turned with the page on every page transition
                // and did not match the shell's, so the picture jumped coming back from a page.
                background = null,
                // The rows inside these sections bring their own 24dp gutter, so the panorama keeps
                // almost none of its own and each section header takes the same indent instead. That
                // way header and rows sit on one edge — see MetroPanoramaSection's headerPadding.
                sectionPadding = PanoramaSectionPadding,
                // Settings → interface. The title is worth a row and a half of library, and whether that
                // is a good trade is the user's call.
                collapsingTitle = settings.collapseTitle,
                sections = sections.mapIndexed { index, section ->
                    MetroPanoramaSection(
                        title = stringResource(sectionTitleOf(section)),
                        key = section,
                        headerPadding = 24.dp,
                        // Only the sections that are lists of named things can be searched; tapping
                        // "more" or "settings" has nothing to filter.
                        onHeaderClick = if (section in Searchable) {
                            { toggleSearch(section) }
                        } else {
                            null
                        }
                    ) {
                      CompositionLocalProvider(LocalMetroColors provides realColors) {
                        // The list gets the height left under the header and the section's own width.
                        val modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // Where this section is is where the panorama is: a section at rest
                            // starts one padding in from the left edge, and every section to the
                            // left of it is a section's width further round.
                            .onGloballyPositioned { coordinates ->
                                val pageAt = page.coordinates
                                    ?.takeIf { it.isAttached }
                                    ?: return@onGloballyPositioned
                                val position = pageAt.localPositionOf(coordinates, Offset.Zero)
                                if (lapPx > 0f) {
                                    travelled =
                                        (index * sectionPx - (position.x - sectionPadPx)).mod(lapPx)
                                    backdropPan.value = coverPan(travelled, sectionPx, sections.size)
                                }
                                sectionTopPx = position.y
                                if (sectionRestPx.isNaN() || position.y > sectionRestPx) {
                                    sectionRestPx = position.y
                                }
                            }
                        when (section) {
                            LibrarySection.Artists -> ArtistsSection(
                                library = library,
                                modifier = modifier,
                                onNavigate = ::leave,
                                picker = picker,
                                search = searchFor(section),
                                onSearchChange = { query = it }
                            )
                            LibrarySection.Albums -> AlbumsSection(
                                library = library,
                                modifier = modifier,
                                onNavigate = ::leave,
                                picker = picker,
                                search = searchFor(section),
                                onSearchChange = { query = it }
                            )
                            LibrarySection.Songs -> SongsSection(
                                library = library,
                                modifier = modifier,
                                currentTrackId = playerState.trackId,
                                actions = actions,
                                // The queue is the list the way the section is showing it — sorted the way
                                // the user asked and filtered by whatever is in the search box — and the
                                // index into it comes with it. Both are resolved on the tap, not during
                                // composition: an indexOf per visible row is O(n) per frame.
                                onPlay = { queue, index ->
                                    services.player.play(queue, index)
                                    searchIn = null
                                    query = ""
                                },
                                search = searchFor(section),
                                onSearchChange = { query = it }
                            )
                            LibrarySection.Genres ->
                                GenresSection(library, modifier, onNavigate, picker)
                            LibrarySection.More -> MoreSection(
                                modifier = modifier,
                                playlistCount = playlists.items.size,
                                scanning = scanning,
                                onRescan = { services.library.rescan() },
                                onNavigate = onNavigate
                            )
                            LibrarySection.History -> HistorySection(
                                modifier = modifier,
                                albums = recentAlbums,
                                loaded = loaded,
                                onNavigate = onNavigate
                            )
                            LibrarySection.Settings -> SettingsSection(modifier, onNavigate)
                        }
                      }
                    }
                }
            )
          }
            // The room the shell's application bar — and the strip raised over it — take at the
            // bottom of the screen. Kept here, inside the page, so the page area itself is the same
            // height on every screen; see LocalHomeBarSpace.
            Spacer(Modifier.height(LocalHomeBarSpace.current))
        }
        HomeHeaders(
            titles = sectionTitles,
            sectionPx = sectionPx,
            widthPx = widthPx,
            travelled = { travelled },
            sectionTop = { sectionTopPx }
        )
        HomeTitle(
            text = stringResource(R.string.home_title),
            travelPx = HomeTitleTravel * widthPx,
            pan = { coverPan(travelled, sectionPx, sections.size) },
            rolledUp = {
                if (sectionTopPx.isNaN() || sectionRestPx.isNaN()) 0f
                else (sectionRestPx - sectionTopPx).coerceAtLeast(0f)
            }
        )
        TrackActionsHost(actions)
        PlaylistPickerHost(picker)
    }
}

/** Where the home page itself is laid out; see the note where it is written. */
private class PageCoordinates {
    var coordinates: LayoutCoordinates? = null
}

/**
 * The section headers — "artists", "albums", the next one leaning in from the right — drawn here
 * rather than by the panorama, in the same places and the same type.
 *
 * The panorama's own headers sit in a band clipped to exactly the height of the text, and Selawik's
 * descenders reach below that height, so the tail of every g, p and y was cut off along a straight
 * line. Its headers cannot be told otherwise, and they are also the controls — a tap on the one in
 * view opens its search box, a tap on the one leaning in goes there — so they are kept, working, and
 * made invisible (the panorama is given a palette with a transparent foreground; the sections get
 * the real one back), and these are drawn over them, unclipped, where they would have been.
 *
 * Where is worked out the way the panorama works it out: the headers are a page apart less the
 * [HeaderPeek], indented by the section padding plus the header's own 24dp, with the ones you are
 * not on dimmed to [AwayHeaderAlpha]; and each one's bottom is the panorama's [PanoramaHeaderGap]
 * above the top of the sections, so they rise with the sections when the title rolls away. Read in the
 * layer, so a swipe moves them without recomposing; and with nothing to press, a tap goes straight
 * through to the invisible header underneath.
 */
@Composable
private fun HomeHeaders(
    titles: List<String>,
    sectionPx: Float,
    widthPx: Int,
    travelled: () -> Float,
    sectionTop: () -> Float
) {
    val density = LocalDensity.current
    val colors = MetroTheme.colors
    val count = titles.size
    if (count == 0 || widthPx <= 0) return
    val spacing = widthPx - with(density) { HeaderPeek.toPx() }
    val indent = with(density) { (PanoramaSectionPadding + 24.dp).toPx() }
    val gap = with(density) { PanoramaHeaderGap.toPx() }
    // At the page's own origin, which is what [sectionTop] is measured against.
    Box(Modifier.fillMaxSize()) {
        titles.forEachIndexed { index, title ->
            Text(
                text = title,
                color = colors.fg,
                fontFamily = MetroSemilight,
                fontSize = HeaderSize,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer {
                        val top = sectionTop()
                        // Round the loop either way, so the section before the first is the last.
                        val away = (index - travelled() / sectionPx + count / 2f)
                            .mod(count.toFloat()) - count / 2f
                        val distance = kotlin.math.abs(away)
                        translationX = away * spacing + indent
                        translationY = if (top.isNaN()) 0f else top - gap - size.height
                        alpha = when {
                            top.isNaN() || distance > 2.2f -> 0f
                            else -> 1f - distance.coerceIn(0f, 1f) * (1f - AwayHeaderAlpha)
                        }
                    }
            )
        }
    }
}

/** The panorama's header geometry, copied so the drawn headers land on the real ones. */
private val HeaderSize = 48.sp
private val HeaderPeek = 96.dp
private val PanoramaHeaderGap = 12.dp
private const val AwayHeaderAlpha = 0.42f

/**
 * The home panorama's giant title, drawn once, where the panorama would draw it and as it would draw
 * it — the framework's 108sp light weight, the same indent, the same trim of the font's blank above the
 * letters, and the same clip under the status bar for when it rolls away.
 *
 * It moves at a fraction of the sections' speed, which is the parallax the phone's panorama is known
 * for: by [pan], 0 to 1 round the lap as the cover moves, so across all the sections the word slides
 * [travelPx] — under a third of the screen — to the left and slides home again on the wrap — never off the
 * screen and never repeated. [rolledUp] is how far the title has given its height up to the list, in
 * px, so it leaves upwards in step with the sections when "roll the title away" is on.
 *
 * Both are read in the layer, so a swipe or a scroll moves it without recomposing anything.
 */
@Composable
private fun HomeTitle(text: String, travelPx: Float, pan: () -> Float, rolledUp: () -> Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clipToBounds()
    ) {
        Text(
            text = text,
            color = MetroTheme.colors.fg,
            fontFamily = MetroLight,
            fontSize = HomeTitleSize,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                // The panorama's: 22dp in, and 6dp under the status bar less the 30dp of blank the
                // font keeps above the letters at this size.
                .offset(x = 22.dp, y = HomeTitleTop - HomeTitleInkTop)
                .graphicsLayer {
                    translationX = -pan() * travelPx
                    translationY = -rolledUp()
                }
        )
    }
}

/** The panorama's title size and its spacing, copied from the framework so the two agree. */
private val HomeTitleSize = 108.sp
private val HomeTitleTop = 22.dp

/** Space over the panorama's title, shared by the panorama's layout and the drawn [HomeTitle]. */
private val PanoramaTitleTopPadding = HomeTitleTop
private val HomeTitleInkTop = 30.dp

/** How far the title slides over a whole lap, as a share of the screen's width. */
private const val HomeTitleTravel = 0.3f

/** The panorama's gutter, which is also the offset of a section at rest from the left edge. */
private val PanoramaSectionPadding = 2.dp

/**
 * Where the cover is in its pan, 0 to 1, for a panorama [travelled] px round a lap of [count]
 * sections each [sectionPx] wide.
 *
 * One way from the first section to the last, at an even rate, so moving on through the sections
 * always moves the picture the same way. Then, during the one swipe that wraps from the last section
 * round to the first, it slides back to the start — which is what the phone's panorama did on the wrap,
 * and the only place a picture that does not tile can go back. The two halves meet at both ends, so it
 * is continuous all the way round and nothing jumps.
 */
private fun coverPan(travelled: Float, sectionPx: Float, count: Int): Float {
    if (count < 2) return 0f
    val forward = sectionPx * (count - 1)
    return if (travelled <= forward) {
        travelled / forward
    } else {
        (1f - (travelled - forward) / sectionPx).coerceIn(0f, 1f)
    }
}

/** The sections whose header opens a search box: the ones that are lists of named things. */
private val Searchable = setOf(
    LibrarySection.Artists,
    LibrarySection.Albums,
    LibrarySection.Songs
)

/**
 * What each section is called, as a resource id.
 *
 * Shared with the interface settings page, which lists the same sections to reorder them: a section
 * named one thing on the panorama and another in the list that reorders it would be a small lie.
 */
@StringRes
internal fun sectionTitleOf(section: LibrarySection): Int = when (section) {
    LibrarySection.Artists -> R.string.section_artists
    LibrarySection.Albums -> R.string.section_albums
    LibrarySection.Songs -> R.string.section_songs
    LibrarySection.Genres -> R.string.section_genres
    LibrarySection.More -> R.string.section_more
    LibrarySection.History -> R.string.section_history
    LibrarySection.Settings -> R.string.section_settings
}

@Composable
private fun MoreSection(
    modifier: Modifier,
    playlistCount: Int,
    scanning: Boolean,
    onRescan: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    LazyColumn(modifier) {
        // Searching the whole library used to be the first row here; it is the application bar's
        // search button now, which every section can reach.
        item {
            ListRow(stringResource(R.string.row_playlists), "$playlistCount") {
                onNavigate(Screen.Playlists)
            }
        }
        item {
            ListRow(
                primary = stringResource(R.string.row_rescan),
                secondary = stringResource(R.string.row_rescan_hint),
                onClick = onRescan
            )
        }
        if (scanning) {
            item {
                Box(Modifier.padding(top = 20.dp, start = 24.dp, end = 24.dp)) {
                    MetroProgressDots()
                }
            }
        }
        // The panorama's sections run to the bottom edge of the screen, so every list inside one
        // ends with the gesture bar's worth of room — the framework's own lists do this themselves.
        item { MetroBottomInset(extra = SectionEndGap) }
    }
}

/** Space after the last row of a hand-rolled section list, matching what MetroLongList leaves. */
private val SectionEndGap = 20.dp

/**
 * Settings as a section of the panorama: an index of pages, not a wall of switches.
 *
 * It is last in the default order and the panorama wraps, so this is one swipe *left* of the landing
 * section — the place a WP8 user would look for it, without it sitting between the library and the
 * music.
 */
@Composable
private fun SettingsSection(modifier: Modifier, onNavigate: (Screen) -> Unit) {
    LazyColumn(modifier) {
        items(SettingsPage.entries, key = { it.name }) { page ->
            SettingsIndexRow(page, onNavigate)
        }
        item { MetroBottomInset(extra = SectionEndGap) }
    }
}

/**
 * One row of the settings index. Its name and description come from the same place the standalone
 * settings page reads them, so the two lists cannot drift apart.
 */
@Composable
private fun SettingsIndexRow(page: SettingsPage, onNavigate: (Screen) -> Unit) {
    ListRow(
        primary = stringResource(titleOf(page)),
        secondary = subtitleOf(page)?.let { stringResource(it) },
        onClick = { onNavigate(Screen.SettingsDetail(page)) }
    )
}

@Composable
private fun HistorySection(
    modifier: Modifier,
    albums: List<Album>,
    loaded: Boolean,
    onNavigate: (Screen) -> Unit
) {
    if (albums.isEmpty()) {
        MetroEmptyNote(
            text = stringResource(
                if (loaded) R.string.empty_history else R.string.empty_history_loading
            ),
            modifier = modifier
        )
        return
    }
    LazyColumn(modifier) {
        items(albums, key = { it.id }) { album ->
            WideTile(
                albumId = album.id,
                representativeTrackId = album.representativeTrackId,
                primary = album.title,
                secondary = album.artist,
                onClick = { onNavigate(Screen.AlbumDetail(album.id)) },
                // No continuum key: an album listed here is usually also in the albums section,
                // and two live elements sharing one key are taken for the same object and laid
                // out on top of each other.
            )
        }
        item { MetroBottomInset(extra = SectionEndGap) }
    }
}

