package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.state.RestingPull
import kotlinx.coroutines.flow.emptyFlow

private const val LONG_COMMENT =
    "The subtitles drift out of sync about halfway through episode three, and they stay a second behind until the credits. " +
        "It happens on the TV and on the phone, so it is not the player. The file is the same one the server shows as direct play, " +
        "so I do not think a transcode is involved. I have not tried another release yet."

/**
 * The issue page: the title it is about, the report, the thread, and — where the viewer may act — the
 * floating composer (`IssueComposer`, drawn by `IssueDetailScreen.kt`'s `Ready`, the one structural
 * branch this screen has). The keyboard-open frames wait on a frame host with an IME inset.
 */
class IssueDetailScreenshotTest {
    /** The Loading arm (#373): the header, the divider, the problem report and the Comments section header. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = IssueDetailSkeleton()

    /** The layout, once: a manager's own page, with the pinned composer/resolve bar showing. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun openWithActions() = Frame(openIssueWithActionsDetail())

    /** A viewer with nothing to do here: resolved, no manage permission — no pinned bar at all. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun resolvedReadOnly() = Frame(resolvedIssueReadOnlyDetail())

    /** A comment typed in the card: Send is enabled, with Resolve beside it. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun composerTyped() = Frame(IssueDetailUiState.Ready(detail = openIssueWithActionsDetail(), draft = "Fixed in the latest build."))

    /** A comment past five lines: the card stops growing and the field scrolls inside it, at every font scale. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun composerLongText() = Frame(IssueDetailUiState.Ready(detail = openIssueWithActionsDetail(), draft = LONG_COMMENT))

    /** Resolve in flight: the chip gives way to a spinner and the field is held. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun composerResolving() =
        Frame(IssueDetailUiState.Ready(detail = openIssueWithActionsDetail(), action = IssueAction.UpdatingStatus))

    /** A row a list already had, before the fetch lands (#1053): its header over the loading body. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun seeded() = Frame(IssueDetailUiState.Seeded(openIssueWithActionsDetail().item))

    /** The same seed after a failed refresh: the header stays, over the error and its retry. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun seededFailed() = Frame(IssueDetailUiState.Seeded(openIssueWithActionsDetail().item, error = SeerrError.Unreachable))

    /** A comment edit on its way to the server (#930): the top bar says Saving beside the page's own actions. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun savingEdit() = Frame(IssueDetailUiState.Ready(detail = openIssueWithActionsDetail(), editStatus = SaveStatus.Saving))

    /** A pull re-reading the issue: the spinner under the top bar, over the thread, which stays as it was. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun refreshing() = Frame(IssueDetailUiState.Ready(detail = openIssueWithActionsDetail(), isRefreshing = true))
}

@Composable
private fun Frame(detail: IssueDetail) = Frame(IssueDetailUiState.Ready(detail = detail))

@Composable
private fun Frame(state: IssueDetailUiState) {
    IssueDetailScreen(
        state = state,
        pullState = RestingPull.takeIf { (state as? IssueDetailUiState.Ready)?.isRefreshing == true },
        events = emptyFlow(),
        actions =
            IssueDetailActions(
                onBack = {},
                onRetry = {},
                onDraftChange = {},
                onPostComment = {},
                onRetryOutbox = {},
                onEditOutbox = { _, _ -> },
                onDropOutbox = {},
                onEditComment = { _, _ -> },
                onRetryEdit = {},
                onDeleteComment = {},
                onToggleStatus = {},
                onDeleteIssue = {},
            ),
    )
}
