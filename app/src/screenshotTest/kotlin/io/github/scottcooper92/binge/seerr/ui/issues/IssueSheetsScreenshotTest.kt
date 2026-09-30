package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews

private val SHEET_WIDTH = 411.dp

/**
 * The stateless bodies of the issue and comment action sheets. Modal windows do not capture, so each
 * frame renders the content on the sheet's own container colour at a phone's width.
 */
class IssueSheetsScreenshotTest {
    /** The issue's overflow for a moderator: the web link and the destructive delete. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun manageWithDelete() = SheetFrame { IssueManageContent(canDelete = true, onOpenWeb = {}, onDelete = {}) }

    /** Without the permission to delete, the web link is all there is. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun manageWithoutDelete() = SheetFrame { IssueManageContent(canDelete = false, onOpenWeb = {}, onDelete = {}) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun commentActions() = SheetFrame { CommentActionsContent(onEdit = {}, onDelete = {}) }

    /** A pending comment whose send failed where a re-send may land: retry leads. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun outboxRetryable() = SheetFrame { OutboxActionsContent(retryable = true, onRetry = {}, onEdit = {}, onDrop = {}) }

    /** A pending comment the server would refuse again: edit or drop, no retry. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun outboxNotRetryable() = SheetFrame { OutboxActionsContent(retryable = false, onRetry = {}, onEdit = {}, onDrop = {}) }
}

@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        content()
    }
}
