package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.AddressHandOff
import com.cooper.binge.companion.seerr.ui.SetupActions
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.ui.requests.ModerationEvent
import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsActions
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsSheet
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val EDITION = "Jellyseerr 2.7.2"

/**
 * What a TV page says or does on its own (#1054): the server's version line, the PIN read aloud, and the request page
 * that closes when its request is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvReadOutsTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    /** The summary's title: commits behind is the more specific news, so it wins over a plain "update available". */
    @Test
    fun aServerBehindByCommitsSaysHowManyRatherThanJustThatAnUpdateIsOut() {
        setSettings(commitsBehind = 3, updateAvailable = true)

        assertTextExists(string(R.string.settings_version_behind, EDITION, 3))
        assertTextAbsent(string(R.string.settings_version_update, EDITION))
    }

    @Test
    fun aServerWithAnUpdateOutSaysSo() {
        setSettings(commitsBehind = 0, updateAvailable = true)

        assertTextExists(string(R.string.settings_version_update, EDITION))
    }

    @Test
    fun anUpToDateServerIsItsEditionAlone() {
        setSettings(commitsBehind = 0, updateAvailable = false)

        assertTextExists(EDITION)
        assertTextAbsent(string(R.string.settings_version_update, EDITION))
    }

    /** The summary is a fact, not a control that cannot be used yet: one item, not announced as disabled (#1534). */
    @Test
    fun theSummaryIsOneItemThatIsNotDisabled() {
        setSettings(commitsBehind = 0, updateAvailable = false)

        composeTestRule
            .onNode(hasText(EDITION) and hasText(SampleSettings.connection.baseUrl))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))
    }

    /** The PIN is announced digit by digit, as the PIN, so a screen reader does not read "four thousand…". */
    @Test
    fun thePinIsReadOutDigitByDigit() {
        val code = AddressHandOff.Listening("http://192.168.1.20:41234/a/k7m2pqx4", pin = "4821")
        val state = SetupUiState.Address(serverUrl = "", insecure = false, isInspecting = false, error = null, handOff = code, code = code)
        composeTestRule.setContent { BingeTvTheme { TvSetupScreen(state = state, actions = NoActions, offerHandOff = true) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasContentDescription(string(R.string.tv_handoff_pin_description, "4 8 2 1"))).assertExists()
    }

    /** A removal leaves the page nothing to show, so it closes; an approval keeps it. */
    @Test
    fun theRequestPageClosesOnceItsRequestIsRemovedAndOnlyThen() {
        val events = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)
        var backs = 0
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestDetailScreen(state = RequestDetailUiState.Ready(detail()), events = events, actions = detailActions { backs++ })
            }
        }
        composeTestRule.waitForIdle()

        events.tryEmit(ModerationEvent.Approved)
        composeTestRule.waitForIdle()
        assertEquals(0, backs)

        events.tryEmit(ModerationEvent.Removed)
        composeTestRule.waitForIdle()
        assertEquals(1, backs)
    }

    private fun setSettings(
        commitsBehind: Int,
        updateAvailable: Boolean,
    ) {
        val state =
            SampleSettings.copy(
                server = SampleSettings.server.copy(commitsBehind = commitsBehind, updateAvailable = updateAvailable),
            )
        composeTestRule.setContent {
            BingeTvTheme {
                TvSettingsSheet(
                    state = state,
                    actions = TvSettingsActions(),
                    onDismissRequest = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertTextExists(text: String) {
        assertEquals(true, composeTestRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertTextAbsent(text: String) {
        assertEquals(true, composeTestRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty())
    }

    private fun detailActions(onBack: () -> Unit) =
        TvRequestDetailActions(
            onBack = onBack,
            onRetry = {},
            onOpenInBinge = null,
            onApprove = {},
            onRetryRequest = {},
            onDecline = {},
            onRemove = {},
            onBlock = {},
            onSetMediaStatus = { _, _, _ -> },
        )

    private fun detail() = requestDetail(actions = RequestActions(canRemove = true), mediaId = null)

    private companion object {
        val NoActions =
            SetupActions(
                onEditAddress = {},
                onInspect = {},
                onChangeServer = {},
                onEditForm = {},
                onConnect = {},
                onPlexLaunched = {},
                onCancelLink = {},
                onRequestPasswordReset = {},
            )
    }
}
