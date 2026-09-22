package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import kotlinx.coroutines.flow.emptyFlow

/**
 * The issue page: the title it is about, the report, the thread, and — where the viewer may act — the
 * pinned composer/resolve bar (`pinnedBar` in `IssueDetailScreen.kt`'s `Ready`, the one structural
 * branch this screen has). No phone screenshot coverage existed for this screen before these frames.
 */
class IssueDetailScreenshotTest {
    /** The Loading arm (#373): the header, the divider, and the Comments section header. */
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
}

@Composable
private fun Frame(detail: IssueDetail) {
    IssueDetailScreen(
        state = IssueDetailUiState.Ready(detail = detail),
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
                onDeleteComment = {},
                onToggleStatus = {},
                onDeleteIssue = {},
            ),
    )
}
