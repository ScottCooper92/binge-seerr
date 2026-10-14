package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.preview.SideSheetFrame

/** A phone-height sheet: a lazy list under an unbounded height would not measure, so the frame bounds it as the real sheet does. */
private val SHEET_HEIGHT = 640.dp

/** Tall enough for five whole season rows above the destination fields, so nothing is clipped mid-row. */
private val SHORT_RUN_SHEET_HEIGHT = 680.dp

/** Tall enough that the season list's visible rows all end on a whole row, so none is clipped mid-row. */
private val LONG_RUN_SHEET_HEIGHT = 700.dp

/** The editor with its season checklist as a region of its own, and the bulk toggle above it (#477). */
class EditRequestSheetScreenshotTest {
    /** Twenty-four seasons, the first three held by the server: the list scrolls, the destination rows and the save stay put. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun longRun() = EditFrame(longRunEditState(), LONG_RUN_SHEET_HEIGHT)

    /** The editor as a page of the actions side sheet, under a Back, at the expanded width. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun expandedPage() = EditFrame(allTickedEditState(), side = true)

    /** Everything changeable is ticked, so the toggle reads "Clear" and the held season stays checked and disabled. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun allTicked() = EditFrame(allTickedEditState(), SHORT_RUN_SHEET_HEIGHT)

    /** Partial requests are off on the server: the season list is absent, and only the destination is the editor's to change (#510). */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun destinationOnly() = EditFrame(destinationOnlyEditState())
}

@Composable
private fun EditFrame(
    edit: EditState,
    height: Dp = SHEET_HEIGHT,
    side: Boolean = false,
) {
    if (side) {
        SideSheetFrame(stacked = true) { EditBody(edit) }
    } else {
        SheetFrame(Modifier.height(height)) { EditBody(edit) }
    }
}

@Composable
private fun EditBody(edit: EditState) {
    EditRequestContent(
        item = pendingDetail().item,
        edit = edit,
        actions =
            EditRequestActions(
                onToggleSeason = {},
                onSelectAllSeasons = {},
                onSelectServer = {},
                onSelectProfile = {},
                onSelectRootFolder = {},
                onToggleTag = {},
                onSave = {},
                onDismiss = {},
            ),
    )
}
