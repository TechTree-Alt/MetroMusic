package com.luna.music.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrocompose.ListRow
import com.metrocompose.MetroBottomInset
import com.metrocompose.MetroContextMenu
import com.metrocompose.MetroEdgeScroll
import com.metrocompose.MetroEmptyNote
import com.metrocompose.MetroListBox
import com.metrocompose.MetroListSort
import com.metrocompose.MetroLongList
import com.luna.music.ui.components.MetroTextBox
import com.luna.music.R
import com.luna.music.core.LocalServices
import com.luna.music.data.model.Album
import com.luna.music.data.model.Library
import com.luna.music.data.model.Track
import com.luna.music.ui.components.AlbumGridTile
import com.luna.music.ui.components.CollectionRowWithActions
import com.luna.music.ui.components.PlaylistPicker
import com.luna.music.ui.components.rememberCollectionActions
import com.luna.music.ui.components.TrackActions
import com.luna.music.ui.components.TrackRowWithActions
import com.luna.music.ui.components.WideTile
import com.luna.music.data.store.AlbumSort
import com.luna.music.data.store.ArtistSort
import com.luna.music.data.store.SongSort
import com.luna.music.ui.LibraryIndex
import com.luna.music.ui.formatAlbumCount
import com.luna.music.ui.formatTrackCount
import com.luna.music.ui.rememberAlbumSorts
import com.luna.music.ui.rememberArtistSorts
import com.luna.music.ui.rememberSongSorts
import com.luna.music.ui.nav.Screen

/**
 * The ways of slicing the library, each one a panorama section on the home screen.
 *
 * They are separate composables rather than branches of one screen because the home panorama
 * composes all of them at once, and because each is a whole long list in its own right — the
 * grouping and the jump grid come from [MetroLongList] per section.
 *
 * Every one of them takes its [modifier] and does nothing about its own size: inside a panorama
 * the section decides that (an explicit width, since the scrolling row measures its children
 * unbounded, and a weight for the height left under the header).
 */
/**
 * A section that can be searched: the box appears above the list when [search] is non-null, which is
 * what tapping the section's header does.
 *
 * The box goes inside the section rather than over the panorama because that is where the thing being
 * searched is — the query belongs to this list, and swiping to the next section should leave it
 * behind rather than carry a stale filter along.
 */
@Composable
private fun SearchableSection(
    modifier: Modifier,
    search: String?,
    onSearchChange: (String) -> Unit,
    list: @Composable ColumnScope.(Modifier) -> Unit
) {
    Column(modifier) {
        if (search != null) {
            MetroTextBox(
                value = search,
                onValueChange = onSearchChange,
                placeholder = stringResource(R.string.search_placeholder),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
                // The box exists because the header was just tapped, so the keyboard comes with it.
                autoFocus = true
            )
        }
        list(Modifier.weight(1f))
    }
}

/**
 * Case-insensitive "contains", which is what people mean by searching a music library — plus the
 * Korean initial-consonant shorthand; see [LibraryIndex.matches].
 */
private fun matches(text: String, query: String?): Boolean = LibraryIndex.matches(text, query)

@Composable
fun ArtistsSection(
    library: Library,
    modifier: Modifier,
    onNavigate: (Screen) -> Unit,
    picker: PlaylistPicker? = null,
    search: String? = null,
    onSearchChange: (String) -> Unit = {}
) {
    if (library.artists.isEmpty()) {
        MetroEmptyNote(stringResource(R.string.empty_no_artists), modifier)
        return
    }
    val services = LocalServices.current
    val settings by services.settings.settings.collectAsStateWithLifecycle()
    val stats by services.stats.stats.collectAsStateWithLifecycle()
    val actions = rememberCollectionActions()
    val shown = remember(library.artists, search) {
        library.artists.filter { matches(it.name, search) }
    }
    val sorts = rememberArtistSorts(library, stats.playCounts)

    SearchableSection(modifier, search, onSearchChange) { listModifier ->
    MetroLongList(
        items = shown,
        key = { it.id },
        sort = sorts[settings.artistSort.ordinal],
        modifier = listModifier,
        filledGroupHeaders = settings.letterTiles,
        sorts = sorts,
        onSortSelected = { index -> services.settings.setArtistSort(ArtistSort.entries[index]) },
        edgeScroll = settings.gestureEdgeScroll
    ) { artist ->
        CollectionRowWithActions(
            key = artist.id,
            actions = actions,
            onPlay = { services.player.play(library.tracksOfArtist(artist.id)) },
            onPlayNext = {
                services.player.playNext(library.tracksOfArtist(artist.id), artist.name)
            },
            onHide = { services.hidden.hideArtist(artist.name) },
            onAddToPlaylist = picker?.let { panel ->
                { panel.open(library.tracksOfArtist(artist.id).map { it.id }) }
            }
        ) {
            ListRow(
                primary = artist.name,
                secondary = "${formatAlbumCount(artist.albumCount)} · " +
                    formatTrackCount(artist.trackCount),
                onClick = { onNavigate(Screen.ArtistDetail(artist.id)) },
                onLongClick = { actions.open(artist.id) }
            )
        }
    }
    }
}

@Composable
fun AlbumsSection(
    library: Library,
    modifier: Modifier,
    onNavigate: (Screen) -> Unit,
    picker: PlaylistPicker? = null,
    search: String? = null,
    onSearchChange: (String) -> Unit = {}
) {
    if (library.albums.isEmpty()) {
        MetroEmptyNote(stringResource(R.string.empty_no_albums), modifier)
        return
    }
    val services = LocalServices.current
    val settings by services.settings.settings.collectAsStateWithLifecycle()
    val actions = rememberCollectionActions()
    val shown = remember(library.albums, search) {
        library.albums.filter { matches(it.title, search) || matches(it.artist, search) }
    }
    val sorts = rememberAlbumSorts()

    if (settings.albumGrid) {
        SearchableSection(modifier, search, onSearchChange) { listModifier ->
            AlbumGrid(
                library = library,
                albums = shown,
                sorts = sorts,
                selected = settings.albumSort.ordinal,
                modifier = listModifier,
                onNavigate = onNavigate,
                picker = picker
            )
        }
        return
    }

    SearchableSection(modifier, search, onSearchChange) { listModifier ->
    MetroLongList(
        items = shown,
        key = { it.id },
        sort = sorts[settings.albumSort.ordinal],
        modifier = listModifier,
        // The same answer as every other list, from settings: two sections disagreeing about their
        // own group letters is what made this section look like a different app.
        filledGroupHeaders = settings.letterTiles,
        sorts = sorts,
        onSortSelected = { index -> services.settings.setAlbumSort(AlbumSort.entries[index]) },
        edgeScroll = settings.gestureEdgeScroll
    ) { album ->
        CollectionRowWithActions(
            key = album.id,
            actions = actions,
            onPlay = { services.player.play(library.tracksOf(album)) },
            onPlayNext = { services.player.playNext(library.tracksOf(album), album.title) },
            onHide = { services.hidden.hideAlbum(album.artist, album.title) },
            onAddToPlaylist = picker?.let { panel ->
                { panel.open(library.tracksOf(album).map { it.id }) }
            },
            onEdit = { onNavigate(Screen.AlbumEdit(album.id)) }
        ) {
            WideTile(
                albumId = album.id,
                representativeTrackId = album.representativeTrackId,
                primary = album.title,
                secondary = album.artist,
                onClick = { onNavigate(Screen.AlbumDetail(album.id)) },
                continuumKey = "album-art-${album.id}",
                onLongClick = { actions.open(album.id) }
            )
        }
    }
    }
}

/**
 * One line of the albums grid: two albums side by side, or one at the end of a group. Every album in
 * a row is under the same heading, so a row has one [label] and the list can group rows exactly as it
 * groups albums.
 */
private data class AlbumRow(val index: Int, val label: String, val left: Album, val right: Album?)

/**
 * The albums section as a two-column grid of covers — the same list underneath, laid out in pairs.
 *
 * Built on [MetroLongList] rather than a lazy grid so that it keeps everything the list has: the
 * group headings, the zoom-out over them, the hold on a heading that changes the arrangement, and
 * the scrub down the edge. The albums are arranged by the chosen [sorts] entry first and split into
 * its runs; each run is then cut into pairs, so a heading never has half a row of the group before it
 * sitting beside the first album of the next. The list is then handed those rows, with an arrangement
 * that keeps them in that order and labels them with their run's heading — and the same zoom-out
 * domain as the album arrangement, so the alphabet still dims the letters you own nothing under.
 */
@Composable
private fun AlbumGrid(
    library: Library,
    albums: List<Album>,
    sorts: List<MetroListSort<Album>>,
    selected: Int,
    modifier: Modifier,
    onNavigate: (Screen) -> Unit,
    picker: PlaylistPicker?
) {
    val services = LocalServices.current
    val settings by services.settings.settings.collectAsStateWithLifecycle()
    val actions = rememberCollectionActions()
    val sort = sorts[selected]

    val rows = remember(albums, sort) {
        val ordered = albums.sortedWith(sort.comparator)
        val out = mutableListOf<AlbumRow>()
        var run = mutableListOf<Album>()
        var label: String? = null
        fun flush() {
            val l = label ?: return
            run.chunked(2).forEach { pair -> out += AlbumRow(out.size, l, pair[0], pair.getOrNull(1)) }
        }
        ordered.forEach { album ->
            val itemLabel = sort.header(album)
            if (itemLabel != label) {
                flush()
                label = itemLabel
                run = mutableListOf()
            }
            run += album
        }
        flush()
        out
    }
    // One row arrangement per album arrangement, so the hold-on-a-heading menu lists the same names
    // and picking one re-arranges the albums; only the one in force is ever used to lay rows out.
    val rowSorts = remember(sorts) {
        sorts.map { albumSort ->
            MetroListSort<AlbumRow>(
                name = albumSort.name,
                comparator = compareBy<AlbumRow> { it.index },
                header = { it.label },
                jumpDomain = albumSort.jumpDomain
            )
        }
    }

    MetroLongList(
        items = rows,
        key = { "row-${it.left.id}" },
        sort = rowSorts[selected],
        modifier = modifier,
        filledGroupHeaders = settings.letterTiles,
        sorts = rowSorts,
        onSortSelected = { index -> services.settings.setAlbumSort(AlbumSort.entries[index]) },
        edgeScroll = settings.gestureEdgeScroll
    ) { row ->
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
            val tile = (maxWidth - GridGap) / 2
            Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                listOf(row.left, row.right).forEach { album ->
                    Box(Modifier.width(tile)) {
                        if (album != null) {
                            CollectionRowWithActions(
                                key = album.id,
                                actions = actions,
                                onPlay = { services.player.play(library.tracksOf(album)) },
                                onPlayNext = {
                                    services.player.playNext(library.tracksOf(album), album.title)
                                },
                                onHide = { services.hidden.hideAlbum(album.artist, album.title) },
                                onAddToPlaylist = picker?.let { panel ->
                                    { panel.open(library.tracksOf(album).map { it.id }) }
                                },
                                onEdit = { onNavigate(Screen.AlbumEdit(album.id)) }
                            ) {
                                AlbumGridTile(
                                    albumId = album.id,
                                    representativeTrackId = album.representativeTrackId,
                                    title = album.title,
                                    artist = album.artist,
                                    size = tile,
                                    onClick = { onNavigate(Screen.AlbumDetail(album.id)) },
                                    continuumKey = "album-art-${album.id}",
                                    onLongClick = { actions.open(album.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The space between two covers, across and down: the gap between tiles on the Start screen. */
private val GridGap = 8.dp

/**
 * Songs, arranged whichever way the user last asked for — the one section where that is offered.
 *
 * The handle is the group header itself: hold it and the four arrangements come up in a WP8 list
 * picker (the framework's, on `MetroListSort`), with the one in force in accent. Holding the heading
 * rather than putting a control in the app bar is what keeps the section a list and not a screen with
 * a toolbar, and the heading is already the thing you press to zoom out to the alphabet.
 *
 * [onPlay] is handed the whole list *as it is arranged*, not just the track: tapping a song plays
 * from there onwards, and "from there" has to mean what is on screen — sorting by play count and
 * tapping the top song would otherwise carry on alphabetically from wherever that song happens to sit
 * in the library.
 */
@Composable
fun SongsSection(
    library: Library,
    modifier: Modifier,
    currentTrackId: Long?,
    actions: TrackActions,
    onPlay: (List<Track>, Int) -> Unit,
    search: String? = null,
    onSearchChange: (String) -> Unit = {}
) {
    if (library.tracks.isEmpty()) {
        MetroEmptyNote(stringResource(R.string.empty_no_songs), modifier)
        return
    }
    val services = LocalServices.current
    val settings by services.settings.settings.collectAsStateWithLifecycle()
    val stats by services.stats.stats.collectAsStateWithLifecycle()
    val shown = remember(library.tracks, search) {
        library.tracks.filter { matches(it.title, search) || matches(it.artist, search) }
    }

    val sorts = rememberSongSorts(stats.playCounts)
    val sort = sorts[settings.songSort.ordinal]

    SearchableSection(modifier, search, onSearchChange) { listModifier ->
        MetroLongList(
            items = shown,
            key = { it.id },
            sort = sort,
            modifier = listModifier,
            filledGroupHeaders = settings.letterTiles,
            sorts = sorts,
            onSortSelected = { index -> services.settings.setSongSort(SongSort.entries[index]) },
            edgeScroll = settings.gestureEdgeScroll
        ) { track ->
            TrackRowWithActions(
                track = track,
                actions = actions,
                // Ordered on the tap rather than per frame: the arranged list is the framework's,
                // and re-deriving it here for every visible row would sort the library each time one
                // scrolled past.
                onPlay = {
                    val queue = shown.sortedWith(sort.comparator)
                    onPlay(queue, queue.indexOf(track))
                },
                isCurrent = track.id == currentTrackId
            )
        }
    }
}

/**
 * Genres, on the platforms that have them.
 *
 * Present even when empty, unlike before: the home panorama wraps and numbers its pages modulo the
 * section count, so a section that appeared when the scan found its first genre tag would shift every
 * other section along. An empty note is the cheaper honesty.
 */
@Composable
fun GenresSection(
    library: Library,
    modifier: Modifier,
    onNavigate: (Screen) -> Unit,
    picker: PlaylistPicker? = null
) {
    if (library.genres.isEmpty()) {
        MetroEmptyNote(stringResource(R.string.empty_no_genres), modifier)
        return
    }
    val services = LocalServices.current
    val settings by services.settings.settings.collectAsStateWithLifecycle()
    val actions = rememberCollectionActions()
    // Which genre is waiting to be merged into another; the picker below is what chooses the target.
    var mergeSource by remember { mutableStateOf<String?>(null) }

    // Counting per genre in one pass beats filtering the whole track list per row.
    val counts = remember(library) { library.tracks.groupingBy { it.genre }.eachCount() }
    val listState = rememberLazyListState()
    // The band down the edge, by hand: genres are one flat list rather than a MetroLongList, so there
    // are no runs to name and the tile carries the letter of the genre reached — which is the same
    // answer the other three sections give, arrived at from a shorter list.
    MetroEdgeScroll(
        state = listState,
        modifier = modifier,
        enabled = settings.gestureEdgeScroll,
        itemCount = library.genres.size,
        label = { index ->
            library.genres.getOrNull(index)
                ?.let { LibraryIndex.bucketOf(it) }
                .orEmpty()
        }
    ) {
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(library.genres, key = { it }) { genre ->
            val tracks = remember(library, genre) { library.tracksOfGenre(genre) }
            // Label and action together rather than a `when` over indices, for the reason
            // [CollectionRowWithActions] gives: one of the entries is conditional.
            val entries = buildList<Pair<String, () -> Unit>> {
                add(stringResource(R.string.action_play) to { services.player.play(tracks) })
                add(
                    stringResource(R.string.menu_play_next) to
                        { services.player.playNext(tracks, genre) }
                )
                if (picker != null) {
                    add(
                        stringResource(R.string.menu_add_to_playlist) to
                            { picker.open(tracks.map { it.id }) }
                    )
                }
                add(stringResource(R.string.menu_merge_genre) to { mergeSource = genre })
            }
            MetroContextMenu(
                expanded = actions.isOpen(genre),
                items = entries.map { it.first },
                onSelect = { index ->
                    actions.close()
                    entries.getOrNull(index)?.second?.invoke()
                },
                onDismiss = { actions.close() }
            ) {
                ListRow(
                    primary = genre,
                    secondary = formatTrackCount(counts[genre] ?: 0),
                    onClick = { onNavigate(Screen.GenreDetail(genre)) },
                    onLongClick = { actions.open(genre) }
                )
            }
        }
        // The section reaches the bottom edge of the screen, so the list carries the gesture bar's
        // room at its end instead of stopping short of it.
        item { MetroBottomInset(extra = 20.dp) }
    }
    }

    // Everything except the genre being merged — merging something into itself is the one choice that
    // cannot mean anything.
    val targets = remember(library.genres, mergeSource) {
        library.genres.filterNot { it == mergeSource }
    }
    MetroListBox(
        visible = mergeSource != null,
        title = stringResource(R.string.menu_merge_genre),
        items = targets,
        onSelect = { index ->
            val from = mergeSource
            if (from != null) services.genres.merge(from, targets[index])
            mergeSource = null
        },
        onDismiss = { mergeSource = null }
    )
}
