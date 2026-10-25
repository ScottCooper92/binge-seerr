package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.preview.SideSheetFrame
import com.cooper.binge.companion.seerr.seerr.SeerrError

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

    /** At the expanded width the actions are a side sheet, and Edit request opens as a page of it. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun actionsExpanded() = SideSheetFrame { ActionsContent(pendingDetail()) }

    /** Settled: the media half leads, and the request half is what is left of it. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun actionsSettled() = SheetFrame { ActionsContent(settledDetail()) }

    /** Files deleted from the sheet (#1720): the row that ran it says so in place, whatever the status now reads. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun actionsFilesDeleted() =
        SheetFrame {
            val detail = settledDetail()
            val media = checkNotNull(detail.media)
            ActionsContent(detail.copy(media = media.copy(instances = media.instances.map { it.copy(filesDeleted = true) })))
        }

    /** The one confirm for a movie with no open request: what Radarr does, and that a running download carries on. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun deleteFilesConfirmMovie() = DeleteFilesConfirmFrame(isTv = false, openRequests = 0, removeRequests = false)

    /** A show with open requests, the box ticked: Sonarr's words, and the confirm names both things it does. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun deleteFilesConfirmShowWithRequests() = DeleteFilesConfirmFrame(isTv = true, openRequests = 2, removeRequests = true)

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

/** The confirm is a modal window, which does not capture, so the frame renders its stateless content with the same copy. */
@Composable
private fun DeleteFilesConfirmFrame(
    isTv: Boolean,
    openRequests: Int,
    removeRequests: Boolean,
) {
    val client = stringResource(if (isTv) R.string.media_client_sonarr else R.string.media_client_radarr)
    BingeConfirmDialogContent(
        title = stringResource(R.string.media_delete_files_confirm_title, client),
        message = deleteFilesMessage(isTv),
        confirmLabel = stringResource(if (removeRequests) R.string.media_delete_files_and_requests else R.string.media_delete_files),
        destructive = true,
        onConfirm = {},
        onDismissRequest = {},
        extraContent =
            if (openRequests > 0) {
                { RemoveRequestsCheckbox(count = openRequests, checked = removeRequests, onCheckedChange = {}) }
            } else {
                null
            },
    )
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
