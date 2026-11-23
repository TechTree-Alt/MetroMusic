package com.luna.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.Metro
import com.metrocompose.MetroRegular
import com.metrocompose.metroContinuum

/**
 * One album in the albums section's grid: its cover as a square tile, edge to edge, with nothing
 * written over or under it — the picture *is* the album, the way a record shelf reads.
 *
 * An album with no cover gets what a WP8 tile with no picture got: a square of one of the phone's
 * tile colours with its name in white in the bottom-left corner, in the tile-label position, so a
 * coverless album is still something you can find by eye rather than a blank in the grid. While the
 * cover is still being looked up the square is the colour alone, so names do not flash up and then
 * vanish under pictures as the grid scrolls.
 *
 * [size] is the tile's width on screen, which is also the size the cover is decoded at.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumGridTile(
    albumId: Long,
    representativeTrackId: Long,
    title: String,
    artist: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    continuumKey: Any? = null,
    onLongClick: (() -> Unit)? = null
) {
    val art = rememberAlbumArtState(albumId, representativeTrackId, size)
    val bitmap = art.bitmap
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(if (continuumKey != null) Modifier.metroContinuum(continuumKey) else Modifier)
            .background(placeholderColor(albumId))
            // The picture carries no words, so a screen reader is told what the tile is.
            .semantics { contentDescription = "$title, $artist" }
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (art.settled) {
            Text(
                text = title,
                // White on a tile colour whatever the theme, as every WP8 tile label was.
                color = Metro.Fg,
                fontFamily = MetroRegular,
                fontSize = 18.sp,
                lineHeight = 21.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            )
        }
    }
}
