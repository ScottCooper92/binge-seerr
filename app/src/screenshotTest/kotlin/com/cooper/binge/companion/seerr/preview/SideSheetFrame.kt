package com.cooper.binge.companion.seerr.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeTaskSheetPanel

/** The height a side sheet's frame is drawn at: a tablet in landscape, which is where the sheet is used. */
private val SIDE_SHEET_FRAME_HEIGHT = 640.dp

/**
 * How a task sheet's page looks at the expanded width: the design system's own side-sheet panel, with the page's body in it.
 * [stacked] draws the page as the second of a stack, under a Back, as an editor opened from an actions sheet is. A frame
 * renders the panel rather than the modal window, which a screenshot cannot capture; every expanded sheet frame draws through
 * this one, as the phone's do through [SheetFrame].
 */
@Composable
internal fun SideSheetFrame(
    stacked: Boolean = false,
    height: Dp = SIDE_SHEET_FRAME_HEIGHT,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.height(height)) {
        BingeTaskSheetPanel(
            pages = if (stacked) listOf("root", "page") else listOf("root"),
            onPush = {},
            onBack = {},
        ) { content() }
    }
}
