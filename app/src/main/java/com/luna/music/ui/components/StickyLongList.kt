package com.luna.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.Metro
import com.metrocompose.MetroListSort
import com.metrocompose.MetroLongList
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroTheme

/**
 * The framework's [MetroLongList], with its group heading pinned to the top of the list.
 *
 * In the framework the letter tile is one more row: it scrolls up off the top like the rest, and the
 * screen is left full of albums with nothing saying which letter they are under. Here a copy of the
 * current group's tile stays at the top while its rows scroll by under it, and the next one, as it
 * arrives, pushes it up and off and takes its place — the WP8 long list's own behaviour.
 *
 * The copy is drawn over the list rather than taking part in it: the groups are worked out exactly as
 * the list works them out (the [sort]'s order, a new group at every change of heading, a heading row
 * before each), so the copy knows which row is which group's heading and sits exactly where that row
 * would. It has nothing to press — a tap goes through to whatever is underneath — so the real heading,
 * once it is back in view, is still the one that opens the alphabet and the arrangements.
 *
 * Same parameters as [MetroLongList], so it is a change of name at the call.
 */
@Composable
fun <T> StickyLongList(
    items: List<T>,
    key: (T) -> Any,
    sort: MetroListSort<T>,
    modifier: Modifier = Modifier,
    filledGroupHeaders: Boolean = true,
    sorts: List<MetroListSort<T>> = emptyList(),
    onSortSelected: (Int) -> Unit = {},
    edgeScroll: Boolean = true,
    row: @Composable (T) -> Unit
) {
    val state = rememberLazyListState()
    Box(modifier) {
        MetroLongList(
            items = items,
            key = key,
            sort = sort,
            modifier = Modifier.fillMaxSize(),
            state = state,
            filledGroupHeaders = filledGroupHeaders,
            sorts = sorts,
            onSortSelected = onSortSelected,
            edgeScroll = edgeScroll,
            row = row
        )
        PinnedGroupHeader(items, sort, state, filledGroupHeaders)
    }
}

/** One group heading: the lazy index of its row in the list, and what it says. */
private class Heading(val index: Int, val label: String)

@Composable
private fun <T> PinnedGroupHeader(
    items: List<T>,
    sort: MetroListSort<T>,
    state: LazyListState,
    filled: Boolean
) {
    // The list's own grouping, redone: sorted by the arrangement, a group at each change of heading,
    // and a heading row ahead of each group that has a heading at all.
    val headings = remember(items, sort) {
        val ordered = items.sortedWith(sort.comparator)
        val found = mutableListOf<Heading>()
        var lazyIndex = 0
        var label: String? = null
        ordered.forEach { item ->
            val itemLabel = sort.header(item)
            if (itemLabel != label) {
                label = itemLabel
                if (itemLabel.isNotEmpty()) {
                    found += Heading(lazyIndex, itemLabel)
                    lazyIndex++
                }
            }
            lazyIndex++
        }
        found
    }
    if (headings.isEmpty()) return

    // Which group the top of the list is in. Derived, so this recomposes when the group changes and
    // not on every pixel of scrolling.
    val current by remember(headings) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            headings.indexOfLast { it.index <= first }
        }
    }
    val heading = headings.getOrNull(current) ?: return
    val next = headings.getOrNull(current + 1)

    Box(
        Modifier
            .padding(start = HeaderStart, top = HeaderTop)
            .graphicsLayer {
                // Pushed up by the next heading as it reaches the top: the pinned tile's bottom
                // stays at that heading's top until it is gone.
                val rowHeight = HeaderRowHeight.toPx()
                val nextTop = next?.let { n ->
                    state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == n.index }?.offset
                }
                translationY = if (nextTop != null && nextTop < rowHeight) {
                    (nextTop - rowHeight).toFloat()
                } else {
                    0f
                }
            }
    ) {
        GroupTile(heading.label, filled)
    }
}

/**
 * The framework's group tile, drawn the same way: a 44dp accent square with the letter at half its
 * height, or as tall and as wide as a word needs; the bare text in the theme's colour when the
 * accent square is switched off.
 */
@Composable
private fun GroupTile(label: String, filled: Boolean) {
    val colors = MetroTheme.colors
    val single = label.length == 1
    Box(
        Modifier
            .then(if (single) Modifier.size(TileSize) else Modifier.height(TileSize).widthIn(min = TileSize))
            .then(if (filled) Modifier.background(colors.accent) else Modifier)
            .then(if (single || !filled) Modifier else Modifier.padding(horizontal = 10.dp)),
        contentAlignment = if (filled) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = if (filled) Metro.Fg else colors.fg,
            fontFamily = MetroSemilight,
            fontSize = if (single) (TileSize.value * 0.5f).sp else (TileSize.value * 0.41f).sp
        )
    }
}

/** The framework's heading row: a 44dp tile, 24dp in, 14dp above it and 6dp below. */
private val TileSize = 44.dp
private val HeaderStart = 24.dp
private val HeaderTop = 14.dp
private val HeaderRowHeight = 14.dp + 44.dp + 6.dp
