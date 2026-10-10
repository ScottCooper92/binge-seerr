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
    /** The issue's overflow with a media server and a *arr service to jump to, alongside the web link. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun manageWithServiceLinks() =
        SheetFrame {
            IssueManageContent("Seerr", "Plex", "Radarr", onOpenWeb = {}, onOpenMediaServer = {}, onOpenService = {})
        }

    /** Without a media server or service URL to offer, the web link is all there is. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun manageWebLinkOnly() = SheetFrame { IssueManageContent("Seerr", null, "Radarr", onOpenWeb = {}) }

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

    /** A pending comment whose send is in flight: Edit and Discard are off until it lands or fails. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun outboxSending() = SheetFrame { OutboxActionsContent(retryable = false, sending = true, onRetry = {}, onEdit = {}, onDrop = {}) }
}

@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        content()
    }
}
