package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.Choice
import com.cooper.binge.companion.seerr.ui.DestinationChoices
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestEditorControls
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestEditActions
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

private const val MAIN = 1
private const val SPARE = 2

/** The TV request page's Edit (#1514): it opens the editor, lands on the first control, and saves or drops the edit. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestEditSheetTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private val editor = FakeEditor()

    @Test
    fun editOpensTheEditorOnTheVersionChoice() {
        setContent(EditState(seasons = emptyList(), destination = destination(), can4k = true))

        actionButton(R.string.tv_detail_edit).assertIsFocused()
        pressOk()

        assertEquals(1, editor.starts)
        option(R.string.media_instance_standard).assertIsFocused()
        option(R.string.settings_service_4k).requestFocus()
        pressOk()
        assertEquals(listOf(true), editor.versions)
    }

    @Test
    fun aServerPickedFromItsStepAppliesAndGoesBackToTheForm() {
        setContent(EditState(seasons = emptyList(), destination = destination()))
        pressOk()

        // No version to choose and no seasons: the sheet lands on the server row.
        option(R.string.advanced_server).assertIsFocused()
        pressOk()
        composeTestRule.onNode(hasText("Spare") and isFocusable()).requestFocus()
        pressOk()

        assertEquals(listOf(SPARE), editor.servers)
        option(R.string.advanced_server).assertIsDisplayed()
    }

    @Test
    fun backDropsTheEditAndReturnsToEdit() {
        setContent(EditState(seasons = emptyList(), destination = destination()))
        pressOk()

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        assertEquals(1, editor.cancels)
        actionButton(R.string.tv_detail_edit).assertIsFocused()
    }

    @Test
    fun aRefusedSaveSaysSoInTheSheet() {
        setContent(EditState(seasons = emptyList(), destination = destination(), failed = true))
        pressOk()

        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed), substring = true).assertIsDisplayed()
        composeTestRule.onNode(hasText(string(R.string.request_edit_save)) and isFocusable()).requestFocus()
        pressOk()
        assertTrue(editor.saves > 0)
    }

    private fun destination() =
        DestinationChoices(
            servers = listOf(Choice(MAIN, "Main"), Choice(SPARE, "Spare")),
            serverId = MAIN,
            profiles = listOf(Choice(1, "HD")),
            profileId = 1,
            rootFolders = listOf("/films"),
            rootFolder = "/films",
        )

    private fun setContent(opened: EditState) {
        var edit by mutableStateOf<EditState?>(null)
        editor.onStart = { edit = opened }
        editor.onCancel = { edit = null }
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestDetailScreen(
                    state = RequestDetailUiState.Ready(requestDetail(canEdit = true), edit = edit),
                    events = emptyFlow(),
                    actions =
                        TvRequestDetailActions(
                            onBack = {},
                            onRetry = {},
                            onOpenInBinge = null,
                            onApprove = {},
                            onRetryRequest = {},
                            onDecline = {},
                            onRemove = {},
                            onBlock = {},
                            onSetMediaStatus = { _, _, _ -> },
                            onReportIssue = { _, _ -> },
                            onDismissReport = {},
                            edit = TvRequestEditActions(onStart = { editor.start() }, controls = editor),
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun actionButton(label: Int) =
        composeTestRule.onNode(
            isFocusable() and
                (hasContentDescription(string(label)) or hasText(string(label)) or hasAnyDescendant(hasText(string(label)))),
        )

    private fun option(label: Int) =
        composeTestRule.onNode(
            isFocusable() and (hasText(string(label)) or hasAnyDescendant(hasText(string(label)))),
        )

    private fun pressOk() {
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
    }
}

/** Records what the sheet asks of the editor, and opens or closes it through the page's state. */
private class FakeEditor : RequestEditorControls {
    var onStart: () -> Unit = {}
    var onCancel: () -> Unit = {}
    var starts = 0
    var cancels = 0
    var saves = 0
    val versions = mutableListOf<Boolean>()
    val servers = mutableListOf<Int>()

    fun start() {
        starts++
        onStart()
    }

    override fun cancel() {
        cancels++
        onCancel()
    }

    override fun toggleSeason(number: Int) = Unit

    override fun selectAllSeasons(selected: Boolean) = Unit

    override fun select4k(is4k: Boolean) {
        versions += is4k
    }

    override fun selectServer(id: Int) {
        servers += id
    }

    override fun selectProfile(id: Int) = Unit

    override fun selectRootFolder(path: String) = Unit

    override fun toggleTag(id: Int) = Unit

    override fun save() {
        saves++
    }
}
