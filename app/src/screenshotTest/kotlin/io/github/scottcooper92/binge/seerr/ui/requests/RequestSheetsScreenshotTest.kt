package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SheetFrame
import io.github.scottcooper92.binge.seerr.seerr.SeerrError

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

    /** A 4K request: the header's 4K label beside the status chip, with the season count under it (#479). */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun actions4kSeason() = SheetFrame { ActionsContent(partiallyAvailable4kDetail()) }

    /** Opened from a list row: the preview's rows under a thin bar while the detail loads (#565). */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun actionsDetailLoading() = SheetFrame { ActionsContent(pendingDetail(), SheetDetailLoad.Loading) }

    /** And when that detail fails, the bar gives way to a one-line retry above the same rows. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun actionsDetailFailed() = SheetFrame { ActionsContent(pendingDetail(), SheetDetailLoad.Failed(SeerrError.Unreachable)) }

    /** Another request's sheet while that request is fetched: it is loaded on open, not held by the page. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun otherRequestLoading() = SheetFrame { RequestSheetPlaceholderContent(state = RequestDetailUiState.Loading, onRetry = {}) }

    /** And when that fetch fails, the same retry every other screen offers. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun otherRequestError() =
        SheetFrame {
            RequestSheetPlaceholderContent(state = RequestDetailUiState.Error(SeerrError.Unreachable), onRetry = {})
        }

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
private fun ActionsContent(
    detail: RequestDetail,
    detailLoad: SheetDetailLoad = SheetDetailLoad.Loaded,
) {
    RequestActionsContent(
        model =
            RequestSheetModel(
                item = detail.item,
                actions = detail.actions,
                canEdit = detail.canEdit,
                media = detail.media.takeIf { detailLoad == SheetDetailLoad.Loaded },
                detailLoad = detailLoad,
            ),
        callbacks = RequestSheetCallbacks(onApprove = {}, onRetry = {}, onDecline = {}, onRemove = {}),
        blockTitle = false,
        onBlockTitleChange = {},
    )
}
