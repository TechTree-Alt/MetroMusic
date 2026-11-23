package com.luna.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.MetroRegular
import com.metrocompose.MetroTheme

/**
 * A text box in the theme's own colours — the app's replacement for the framework's.
 *
 * The framework's box is filled with the theme's *foreground*: a white box with black text under the
 * dark theme and a black one under the light theme, an inverted block that is the loudest thing on
 * any page it is on — the search box over a list most of all. This one is a field of the surface
 * colour instead: a dark grey well with white text in the dark theme, a light grey one with black
 * text in the light theme, outlined in the dim grey and in the accent while it has the cursor. The
 * same signature, so it is a change of import and nothing else.
 *
 * [autoFocus] takes the focus — and with it the keyboard — as soon as the box appears, for a box that
 * only exists because the user just asked for it.
 */
@Composable
fun MetroTextBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    autoFocus: Boolean = false
) {
    val colors = MetroTheme.colors
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val dark = colors.bg.luminance() < 0.5f
    val well = if (dark) DarkWell else LightWell

    if (autoFocus) {
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.fg, fontFamily = MetroRegular, fontSize = 20.sp),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .background(well)
            .border(2.dp, if (focused) colors.accent else colors.dim)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.subtle, fontFamily = MetroRegular, fontSize = 20.sp)
                }
                inner()
            }
        }
    )
}

/**
 * The well's fill: the phone's own surface greys, slightly see-through so the backdrop is not cut
 * out by a solid slab.
 */
private val DarkWell = Color(0xE61F1F1F)
private val LightWell = Color(0xE6EBEBEB)
