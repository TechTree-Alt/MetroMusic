package com.luna.music.ui.screens

import com.luna.music.ui.components.AppBarMenuItem
import com.luna.music.ui.components.ApplicationBar
import com.luna.music.ui.components.glyphAction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrocompose.MetroEmptyNote
import com.metrocompose.MetroPage
import com.luna.music.R
import com.luna.music.core.LocalServices
import com.luna.music.ui.Glyphs
import com.luna.music.ui.components.PlaylistPickerHost
import com.luna.music.ui.components.TrackActionsHost
import com.luna.music.ui.components.TrackRowWithActions
import com.luna.music.ui.components.rememberPlaylistPicker
import com.luna.music.ui.components.rememberTrackActions

/** Everything tagged with one genre. */
@Composable
fun GenreDetailScreen(genre: String) {
    val services = LocalServices.current
    val library by services.library.library.collectAsStateWithLifecycle()
    val playerState by services.player.state.collectAsStateWithLifecycle()
    val actions = rememberTrackActions()
    val picker = rememberPlaylistPicker()

    val tracks = remember(library, genre) { library.tracksOfGenre(genre) }

    Box(Modifier.fillMaxSize()) {
        MetroPage(stringResource(R.string.overline_genre), genre) {
            if (tracks.isEmpty()) {
                MetroEmptyNote(stringResource(R.string.genre_gone))
                return@MetroPage
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(tracks, key = { it.id }) { track ->
                    TrackRowWithActions(
                        track = track,
                        actions = actions,
                        onPlay = { services.player.play(tracks, tracks.indexOf(track)) },
                        isCurrent = track.id == playerState.trackId
                    )
                }
            }
            ApplicationBar(
                listOf(
                    glyphAction(Glyphs.Play, stringResource(R.string.action_play)) {
                        services.player.play(tracks, 0)
                    },
                    glyphAction(Glyphs.Shuffle, stringResource(R.string.action_shuffle)) {
                        services.player.shuffleAll(tracks)
                    }
                ),
                menuItems = listOf(
                    AppBarMenuItem(stringResource(R.string.menu_add_to_playlist)) {
                        picker.open(tracks.map { it.id })
                    }
                )
            )
        }
        TrackActionsHost(actions)
        PlaylistPickerHost(picker)
    }
}
