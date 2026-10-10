package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SheetFrame
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission

/**
 * The permissions editor's three row states side by side: flippable, implied by a grant above it,
 * and locked because the viewer may not grant it.
 *
 * The two that cannot be flipped are the point. Their labels read as live until #310, and unlike a
 * control disabled while a save runs, they stay that way for as long as the sheet is open — so a
 * frame is the only thing that can tell whether they dim.
 */
class PermissionsEditorScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun rowStates() {
        SheetFrame {
            PermissionsEditorContent(
                offered =
                    listOf(
                        ManageablePermission.ManageRequests,
                        ManageablePermission.ViewRequests,
                        ManageablePermission.Request,
                        ManageablePermission.ManageUsers,
                    ),
                // ManageRequests implies ViewRequests, so that row shows on and cannot be flipped.
                selected = setOf(ManageablePermission.ManageRequests),
                title = "Permissions",
                saving = false,
                onToggle = {},
                onSave = {},
                locked = setOf(ManageablePermission.ManageUsers),
            )
        }
    }

    /**
     * A bulk edit over users who differ (#1100): Manage issues is still as each user has it and says so; Create issues was
     * mixed too and has been set off for everyone, so it reads as a plain off.
     */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun bulkMixed() {
        SheetFrame {
            PermissionsEditorContent(
                offered = listOf(ManageablePermission.Request, ManageablePermission.CreateIssues, ManageablePermission.ManageIssues),
                selected = setOf(ManageablePermission.Request),
                title = "Edit 2 users",
                saving = false,
                onToggle = {},
                onSave = {},
                note = stringResource(R.string.users_edit_permissions_mixed),
                mixed = setOf(ManageablePermission.ManageIssues, ManageablePermission.CreateIssues),
                undecided = setOf(ManageablePermission.ManageIssues),
            )
        }
    }
}
