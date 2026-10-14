package com.cooper.binge.companion.seerr.ui.users

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.preview.SideSheetFrame

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

    /** At the expanded width the add-user choice is the root page of a side sheet. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun admissionChoiceExpanded() = SideSheetFrame { AdmissionChoiceContent(importSource = UserOrigin.Plex, actions = noActions()) }

    /** The import checklist as the page a choice opens inside the side sheet, under a Back. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun importChecklistExpanded() =
        SideSheetFrame(stacked = true) {
            ImportUsersContent(
                picker =
                    ImportPicker(
                        source = UserOrigin.Plex,
                        candidates =
                            listOf(
                                ImportCandidate(id = "1", name = "Ada Lovelace", email = "ada@example.com", avatarUrl = null),
                                ImportCandidate(id = "2", name = "Grace Hopper", email = null, avatarUrl = null),
                            ),
                        selected = setOf("2"),
                    ),
                saving = false,
                actions = noActions(),
            )
        }

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
        onBackToChoice = {},
        onStartCreate = {},
        onEditDraft = {},
        onCreate = {},
        onStartImport = {},
        onToggleCandidate = {},
        onSelectAllCandidates = {},
        onImport = {},
    )
