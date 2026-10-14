package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.Choice
import com.cooper.binge.companion.seerr.ui.DestinationChoices
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestEditorControls
import com.cooper.binge.companion.seerr.ui.requests.SeasonChoice
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestEditActions
import com.cooper.binge.companion.seerr.util.assertNoCroppedText
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The TV request editor's rows at 1×: a season's line saying how many episodes it has, or that the server already holds
 * it, and the three destination rows naming their current choice. Each is one short line, and none is cut.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class TvEditSheetCroppedText {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSeasonsAndTheDestinationFit() {
        val opened =
            EditState(
                seasons =
                    listOf(
                        SeasonChoice(
                            number = 1,
                            name = null,
                            episodeCount = 10,
                            selected = true,
                            heldStatus = SeerrMediaStatusCode.Available,
                        ),
                        SeasonChoice(number = 2, name = null, episodeCount = 8, selected = false),
                        SeasonChoice(number = 3, name = "Specials", episodeCount = 1, selected = true),
                        SeasonChoice(
                            number = 4,
                            name = null,
                            episodeCount = 6,
                            selected = false,
                            heldStatus = SeerrMediaStatusCode.Processing,
                        ),
                    ),
                destination =
                    DestinationChoices(
                        servers = listOf(Choice(1, "Sonarr")),
                        serverId = 1,
                        profiles = listOf(Choice(1, "Ultra-HD")),
                        profileId = 1,
                        rootFolders = listOf("/media/tv"),
                        rootFolder = "/media/tv",
                    ),
            )
        setContent(opened)
        composeTestRule.onNodeWithText("Sonarr").assertExists()
        composeTestRule.assertNoCroppedText("the request editor")
    }

    private fun setContent(opened: EditState) {
        var edit by mutableStateOf<EditState?>(null)
        val editor = Editor(onStart = { edit = opened })
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
                            edit = TvRequestEditActions(onStart = editor::start, controls = editor),
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
    }
}

private class Editor(
    private val onStart: () -> Unit,
) : RequestEditorControls {
    fun start() = onStart()

    override fun cancel() = Unit

    override fun toggleSeason(number: Int) = Unit

    override fun selectAllSeasons(selected: Boolean) = Unit

    override fun selectServer(id: Int) = Unit

    override fun selectProfile(id: Int) = Unit

    override fun selectRootFolder(path: String) = Unit

    override fun toggleTag(id: Int) = Unit

    override fun save() = Unit
}

/** [TvEditSheetCroppedText] in English. */
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvEditSheetCroppedTextTest : TvEditSheetCroppedText()

/** [TvEditSheetCroppedText] in Spanish. */
@Config(qualifiers = "es-w960dp-h540dp-television-xhdpi")
class TvEditSheetCroppedTextSpanishTest : TvEditSheetCroppedText()
