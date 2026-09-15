package io.github.scottcooper92.binge.seerr.ui.requests

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
 * The two sheets the page's bar and primary open. Modal windows do not capture, so both frames
 * render the stateless content on the sheet's own container colour at a phone's width.
 */
class RequestSheetsScreenshotTest {
    /** Pending: the request half leads, because there is still an approve to make. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun actionsPending() = SheetFrame { ActionsContent(pendingDetail()) }

    /** Settled: the media half leads, and the request half is what is left of it. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun actionsSettled() = SheetFrame { ActionsContent(settledDetail()) }

    /** The three Open-in links, which used to have two homes and now have one. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun openElsewhere() =
        SheetFrame {
            RequestOpenSheetContent(links = rememberRequestOpenLinks(pendingDetail()), onOpen = {})
        }
}

@Composable
private fun ActionsContent(detail: RequestDetail) {
    RequestActionsContent(
        model =
            RequestSheetModel(
                item = detail.item,
                actions = detail.actions,
                canEdit = detail.canEdit,
                media = detail.media,
            ),
        callbacks = RequestSheetCallbacks(onApprove = {}, onRetry = {}, onDecline = {}, onRemove = {}),
        blockTitle = false,
        onBlockTitleChange = {},
    )
}

@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        content()
    }
}
