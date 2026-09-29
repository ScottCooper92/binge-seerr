package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews

private val SHEET_WIDTH = 411.dp

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

    /** Everything changeable is ticked, so the toggle reads "Clear" and the held season stays checked and disabled. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun allTicked() = EditFrame(allTickedEditState(), SHORT_RUN_SHEET_HEIGHT)
}

@Composable
private fun EditFrame(
    edit: EditState,
    height: Dp = SHEET_HEIGHT,
) {
    Box(Modifier.width(SHEET_WIDTH).height(height).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
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
}
