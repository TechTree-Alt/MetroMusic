package com.luna.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.LocalMetroHasBackdrop
import com.metrocompose.MetroLight
import com.metrocompose.MetroTheme

/** The sizes a page title may take, largest first — the framework's own ramp for `MetroPage`. */
private val TitleSizes = listOf(52.sp, 46.sp, 40.sp, 34.sp, 30.sp, 26.sp)

/**
 * `MetroPage` without the overline: the big light title, then the content.
 *
 * For a page whose title says everything the overline would — an album, whose artist is already on
 * the page as a link beside the cover. The frame is the framework's to the dp (the 52dp top, the
 * insets, the backdrop rule) and the title fits itself the same way, at the largest size of the ramp
 * that fits the window, so the page turns in and lays out exactly as a `MetroPage` would, one line
 * shorter.
 */
@Composable
fun TitledPage(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MetroTheme.colors
    // Inside a backdrop the app has already painted the page; painting the flat colour over it is
    // what would make this one screen black.
    val opaque = !LocalMetroHasBackdrop.current
    Column(
        modifier
            .fillMaxSize()
            .then(if (opaque) Modifier.background(colors.bg) else Modifier)
            .navigationBarsPadding()
            .padding(top = 52.dp)
    ) {
        PageTitle(title)
        content()
    }
}

@Composable
private fun PageTitle(title: String) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.padding(start = 22.dp, end = 16.dp, top = 2.dp, bottom = 22.dp)) {
        val available = constraints.maxWidth
        val size = remember(title, available, measurer) {
            TitleSizes.firstOrNull { candidate ->
                measurer.measure(
                    text = AnnotatedString(title),
                    style = TextStyle(fontFamily = MetroLight, fontSize = candidate),
                    maxLines = 1,
                    softWrap = false
                ).size.width <= available
            }
        }
        Text(
            text = title,
            color = MetroTheme.colors.fg,
            fontFamily = MetroLight,
            fontSize = size ?: TitleSizes.last(),
            maxLines = 1,
            softWrap = false,
            overflow = if (size == null) TextOverflow.Ellipsis else TextOverflow.Visible
        )
    }
}
