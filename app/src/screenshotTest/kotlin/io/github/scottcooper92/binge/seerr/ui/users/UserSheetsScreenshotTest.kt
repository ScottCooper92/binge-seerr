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

private val SHEET_WIDTH = 411.dp

/**
 * The action and picker sheets on the users pages, as the stateless bodies a modal window will not capture: each is an
 * item group, with its link marked as leaving the app and its delete in the error colour.
 */
class UserSheetsScreenshotTest {
    /** The user page's overflow: their settings, the user on the server (a link), and delete. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun userActions() =
        SheetFrame {
            UserActionsContent(manageableUserDetail(), deleting = false, onOpenSettings = {}, onOpenWeb = {}, onDelete = {})
        }

    /** Delete while one is in flight: dimmed and inert. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun userActionsDeleting() =
        SheetFrame {
            UserActionsContent(manageableUserDetail(), deleting = true, onOpenSettings = {}, onOpenWeb = {}, onDelete = {})
        }

    /** Where the server has a media server, a local account or an import. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun admissionChoice() = SheetFrame { AdmissionChoiceContent(importSource = UserOrigin.Plex, actions = noActions()) }

    /** The import checklist: each account a checkbox row with its avatar, one ticked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun importChecklist() =
        SheetFrame {
            ImportUsersContent(
                picker =
                    ImportPicker(
                        source = UserOrigin.Plex,
                        candidates =
                            listOf(
                                ImportCandidate(id = "1", name = "Ada Lovelace", email = "ada@example.com", avatarUrl = null),
                                ImportCandidate(id = "2", name = "Grace Hopper", email = null, avatarUrl = null),
                                ImportCandidate(id = "3", name = "Katherine Johnson", email = "katherine@example.com", avatarUrl = null),
                            ),
                        selected = setOf("2"),
                    ),
                saving = false,
                actions = noActions(),
            )
        }
}

private fun noActions() =
    UserAdmissionActions(
        onStart = {},
        onCancel = {},
        onStartCreate = {},
        onEditDraft = {},
        onCreate = {},
        onStartImport = {},
        onToggleCandidate = {},
        onSelectAllCandidates = {},
        onImport = {},
    )

@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        content()
    }
}
