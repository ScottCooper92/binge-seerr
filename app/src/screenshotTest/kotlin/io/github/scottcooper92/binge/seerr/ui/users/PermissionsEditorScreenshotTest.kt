package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission

private val SHEET_WIDTH = 411.dp

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
        Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
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
}
