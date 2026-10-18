package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.preview.SideSheetFrame

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

    /** The composer's empty draft, with Send off until there is something to send. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun commentComposerEmpty() = SheetFrame { CommentComposerContent(draft = "", onDraftChange = {}, onSubmit = {}, onCancel = {}) }

    /** At the expanded width the composer is a side sheet's page. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun commentComposerExpanded() =
        SideSheetFrame {
            CommentComposerContent(draft = "The subtitles drift two seconds out of sync.", onDraftChange = {}, onSubmit = {}, onCancel = {})
        }

    /** Editing: Done stays off until the text differs from what was sent. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun editCommentChanged() =
        SheetFrame {
            EditCommentContent(
                title = "Edit comment",
                draft = "Subtitles drift by three seconds.",
                original = "Subtitles drift by two seconds.",
                onDraftChange = {},
                onSubmit = {},
                onCancel = {},
            )
        }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun editCommentExpanded() =
        SideSheetFrame {
            EditCommentContent(
                title = "Edit comment",
                draft = "Subtitles drift by three seconds.",
                original = "Subtitles drift by two seconds.",
                onDraftChange = {},
                onSubmit = {},
                onCancel = {},
            )
        }
}
