package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.tv.material3.Text
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsConsent
import io.github.scottcooper92.binge.seerr.telemetry.TelemetryPrefs
import io.github.scottcooper92.binge.seerr.ui.consent.ConsentViewModel
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val CONTENT = "Setup goes on"
private const val WAIT_MILLIS = 5_000L

/**
 * The TV's consent question under a real D-pad (#1054): its answers, and the gate that asks it once, before setup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvConsentGateTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun shareTakesTheArrivalFocusAndOkAgrees() {
        val choices = mutableListOf<Boolean>()
        composeTestRule.setContent { BingeTvTheme { TvConsentScreen(onChoice = { choices += it }) } }
        composeTestRule.waitForIdle()

        answer(R.string.consent_accept).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf(true), choices)
    }

    @Test
    fun notNowBesideItDeclines() {
        val choices = mutableListOf<Boolean>()
        composeTestRule.setContent { BingeTvTheme { TvConsentScreen(onChoice = { choices += it }) } }
        composeTestRule.waitForIdle()

        press(Key.DirectionRight)
        answer(R.string.consent_decline).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf(false), choices)
    }

    /** An unanswered install is asked before setup, and the answer is kept: what the gate holds back comes next. */
    @Test
    fun theGateAsksOnceThenLetsSetupThrough() {
        val prefs = TelemetryPrefs(PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("t.preferences_pb") })
        val viewModel = ConsentViewModel(prefs, Dispatchers.IO)
        composeTestRule.setContent { BingeTvTheme { TvConsentGate(viewModel = viewModel) { Text(CONTENT) } } }

        composeTestRule.waitUntil(WAIT_MILLIS) { answerExists(R.string.consent_decline) }
        composeTestRule.onNodeWithText(CONTENT).assertDoesNotExist()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        composeTestRule.waitUntil(WAIT_MILLIS) { composeTestRule.onAllNodes(hasText(CONTENT)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(AnalyticsConsent.DENIED, runBlocking { prefs.analyticsConsent.first() })
    }

    private fun answer(id: Int) = composeTestRule.onNode(hasText(string(id)) and isFocusable())

    private fun answerExists(id: Int) = composeTestRule.onAllNodes(hasText(string(id)) and isFocusable()).fetchSemanticsNodes().isNotEmpty()

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}
