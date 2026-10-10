package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.DestinationChoices
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.requestItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The request editor swaps a picker in for its form when a server, profile or root folder field is tapped (#336). The
 * swap is the editor's own state, so these tests host the real [EditRequestContent] and check what the user can do:
 * open a picker, pick a row, and come back without picking.
 */
@RunWith(RobolectricTestRunner::class)
class EditRequestDestinationPickerTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val picks = mutableListOf<String>()
    private var dispatcher: OnBackPressedDispatcher? = null

    private val actions =
        EditRequestActions(
            onToggleSeason = {},
            onSelectAllSeasons = {},
            onSelectServer = { picks += "server $it" },
            onSelectProfile = { picks += "profile $it" },
            onSelectRootFolder = { picks += "folder $it" },
            onToggleTag = {},
            onSave = {},
            onDismiss = {},
        )

    private val destination =
        DestinationChoices(
            servers = listOf(Choice(1, "Sonarr"), Choice(2, "Sonarr 4K")),
            serverId = 1,
            profiles = listOf(Choice(6, "HD-1080p"), Choice(7, "Ultra-HD")),
            profileId = 6,
            rootFolders = listOf("/tv", "/anime"),
            rootFolder = "/tv",
        )

    private fun show(destination: DestinationChoices = this.destination) {
        rule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            SeerrTheme {
                // A sheet is bounded in height, which is what a long list has to fit within.
                Box(Modifier.height(480.dp)) {
                    EditRequestContent(
                        item = item(),
                        edit = EditState(seasons = emptyList(), destination = destination, seasonsEditable = false),
                        actions = actions,
                    )
                }
            }
        }
    }

    private fun item() = requestItem(mediaType = RequestMediaType.Tv, title = "Severance", year = "2022", status = null)

    private fun string(
        @androidx.annotation.StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun saveShown() = rule.onNodeWithText(string(R.string.request_edit_save))

    private fun open(
        @androidx.annotation.StringRes field: Int,
    ) = rule.onNodeWithText(string(field)).performClick()

    @Test
    fun `tapping a destination field shows its picker in place of the form`() {
        show()
        saveShown().assertIsDisplayed()

        open(R.string.advanced_profile)

        rule.onNodeWithText("Ultra-HD").assertIsDisplayed()
        saveShown().assertDoesNotExist()
    }

    @Test
    fun `picking a row reports the pick and returns to the form`() {
        show()

        open(R.string.advanced_server)
        rule.onNodeWithText("Sonarr 4K").performClick()

        assertEquals(listOf("server 2"), picks)
        saveShown().assertIsDisplayed()
    }

    @Test
    fun `each field's picker reports its own kind of pick`() {
        show()

        open(R.string.advanced_profile)
        rule.onNodeWithText("Ultra-HD").performClick()
        open(R.string.advanced_root_folder)
        rule.onNodeWithText("/anime").performClick()

        assertEquals(listOf("profile 7", "folder /anime"), picks)
    }

    @Test
    fun `the back arrow returns to the form without a pick`() {
        show()

        open(R.string.advanced_server)
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(emptyList<String>(), picks)
        saveShown().assertIsDisplayed()
    }

    @Test
    fun `system back returns to the form without a pick`() {
        show()

        open(R.string.advanced_server)
        rule.runOnIdle { dispatcher?.onBackPressed() }

        assertEquals(emptyList<String>(), picks)
        saveShown().assertIsDisplayed()
    }

    @Test
    fun `a long list fits the sheet and scrolls to its last row`() {
        val folders = (1..14).map { "/media/folder-%02d".format(it) }
        show(destination.copy(rootFolders = folders, rootFolder = folders.first()))

        open(R.string.advanced_root_folder)
        // A list this long is sectioned: the chosen row is in "Current" and again in the full list, so it is read twice.
        rule.onAllNodesWithText(folders.first()).onFirst().assertIsDisplayed()
        rule.onNodeWithText(folders.last()).assertIsNotDisplayed()

        rule.onNode(hasScrollAction()).performScrollToNode(hasText(folders.last()))
        rule.onNodeWithText(folders.last()).assertIsDisplayed()
    }
}
