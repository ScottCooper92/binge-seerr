package com.cooper.binge.companion.seerr.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * How a bottom sheet's body looks: a phone's width on the sheet surface. A frame renders the stateless body rather than
 * the modal sheet, which a screenshot cannot capture. Every sheet frame draws through this one, so they cannot disagree
 * about what a sheet is. [modifier] adds to it, for a sheet a frame fixes the height of.
 */
@Composable
internal fun SheetFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(Modifier.width(PHONE_WIDTH).then(modifier).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        content()
    }
}

/**
 * How a side sheet's body looks in a wide window: the end-edge column the adaptive sheet takes at 840dp and up, full
 * height, with the window's own surface beside it. A frame renders the stateless body, as [SheetFrame] does.
 */
@Composable
internal fun SideSheetFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(SIDE_SHEET_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            content()
        }
    }
}

private val SIDE_SHEET_WIDTH = 420.dp
