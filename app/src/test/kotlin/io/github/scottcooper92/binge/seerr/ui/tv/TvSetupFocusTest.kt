package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupServer
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.SignInForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The setup steps under a real D-pad. Robolectric hosts Compose on the JVM, and the television qualifier
 * is load-bearing: a 2D focus search on a handset's screen behaves nothing like a panel's.
 *
 * Every walk starts from where the page lands the remote and moves one press at a time, so an assertion
 * names the control that stopped answering rather than the last one that did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi", application = android.app.Application::class)
class TvSetupFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val edits = mutableListOf<String>()
    private val formEdits = mutableListOf<SignInForm.() -> SignInForm>()
    private var inspected = 0
    private var connected = 0
    private var changedServer = 0

    private val actions =
        SetupActions(
            onEditAddress = { edits += it },
            onInspect = { inspected++ },
            onChangeServer = { changedServer++ },
            onEditForm = { formEdits += it },
            onConnect = { connected++ },
            onPlexLaunched = {},
            onCancelLink = {},
            onRequestPasswordReset = {},
        )

    @Test
    fun theAddressStepLandsOnTheField() {
        setScreen(address("http://seerr.lan:5055"))

        addressField().assertIsFocused()
    }

    @Test
    fun typingIntoTheAddressReportsEachEdit() {
        setScreen(address(""))

        addressField().performTextInput("h")

        assertEquals(listOf("h"), edits)
    }

    @Test
    fun downFromTheAddressReachesContinueAndOkInspects() {
        setScreen(address("http://seerr.lan:5055"))

        pressDown()
        button(R.string.setup_continue).assertIsFocused()
        pressOk()

        assertEquals(1, inspected)
    }

    @Test
    fun anEmptyAddressLeavesContinueDisabledButFocusable() {
        setScreen(address(""))

        pressDown()
        button(R.string.setup_continue).assertIsFocused()
        pressOk()

        assertEquals("a blank address must not be inspected", 0, inspected)
    }

    @Test
    fun theSignInStepOffersOnlyTheModesARemoteCanType() {
        setScreen(signIn(modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.Local, SeerrSignInMode.ApiKey)))

        modeRow(R.string.setup_mode_local).assertIsDisplayed()
        modeRow(R.string.setup_mode_api_key).assertIsDisplayed()
        assertTrue(
            "Plex finishes on another device, so the TV must not offer it",
            composeTestRule.onAllNodes(hasText(string(R.string.setup_mode_plex))).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun theSignInStepWalksModesThenTheFieldThenConnect() {
        setScreen(
            signIn(
                modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.Local, SeerrSignInMode.ApiKey),
                form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = "k3y"),
            ),
        )

        modeRow(R.string.setup_mode_local).assertIsFocused()
        pressDown()
        modeRow(R.string.setup_mode_api_key).assertIsFocused()
        pressDown()
        field(R.string.setup_api_key).assertIsFocused()
        pressDown()
        button(R.string.setup_connect).assertIsFocused()
        pressOk()

        assertEquals(1, connected)
    }

    @Test
    fun okOnAModeRowChangesTheMode() {
        setScreen(
            signIn(
                modes = listOf(SeerrSignInMode.Local, SeerrSignInMode.ApiKey),
                form = SignInForm(mode = SeerrSignInMode.ApiKey),
            ),
        )

        modeRow(R.string.setup_mode_local).assertIsFocused()
        pressOk()

        val applied = formEdits.fold(SignInForm(mode = SeerrSignInMode.ApiKey)) { form, edit -> form.edit() }
        assertEquals(SeerrSignInMode.Local, applied.mode)
    }

    @Test
    fun okOnTheCurrentModeIsANoOp() {
        setScreen(
            signIn(
                modes = listOf(SeerrSignInMode.ApiKey, SeerrSignInMode.Local),
                form = SignInForm(mode = SeerrSignInMode.ApiKey),
            ),
        )

        modeRow(R.string.setup_mode_api_key).assertIsFocused()
        pressOk()

        assertTrue("re-pressing the chosen mode must not re-commit it", formEdits.isEmpty())
    }

    @Test
    fun aModeTheTvCannotFinishIsReplacedByOneItCan() {
        setScreen(
            signIn(
                modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.ApiKey),
                form = SignInForm(mode = SeerrSignInMode.Plex),
            ),
        )

        val applied = formEdits.fold(SignInForm(mode = SeerrSignInMode.Plex)) { form, edit -> form.edit() }
        assertEquals(SeerrSignInMode.ApiKey, applied.mode)
    }

    @Test
    fun aServerWithNothingToTypeSaysSoAndLandsOnChangeServer() {
        setScreen(signIn(modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect)))

        composeTestRule.onNodeWithText(string(R.string.tv_setup_no_typed_modes)).assertIsDisplayed()
        button(R.string.setup_change_server).assertIsFocused()
        pressOk()

        assertEquals(1, changedServer)
    }

    private fun setScreen(state: SetupUiState) {
        composeTestRule.setContent {
            BingeTvTheme { TvSetupScreen(state = state, actions = actions) }
        }
        composeTestRule.waitForIdle()
    }

    private fun addressField() = field(R.string.setup_server_url)

    private fun field(label: Int) = composeTestRule.onNode(hasSetTextAction() and hasContentDescription(string(label)))

    // A disabled button is a focusable surface over its own text node rather than one merged node, so the
    // label may sit a level below the node that carries focus.
    private fun button(label: Int) =
        composeTestRule.onNode(
            (hasText(string(label)) or hasAnyDescendant(hasText(string(label)))) and isFocusable(),
        )

    private fun modeRow(label: Int) = composeTestRule.onNode(hasText(string(label)) and isFocusable())

    private fun pressDown() {
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
    }

    private fun pressOk() {
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun address(serverUrl: String) =
        SetupUiState.Address(serverUrl = serverUrl, insecure = false, isInspecting = false, error = null)

    private fun signIn(
        modes: List<SeerrSignInMode>,
        form: SignInForm = SignInForm(mode = modes.first()),
    ) = SetupUiState.SignIn(
        server =
            SetupServer(
                baseUrl = "http://seerr.lan:5055",
                title = "Living room",
                variant = SeerrVariant.Jellyseerr,
                versionLabel = "2.7.2",
                mediaServerName = null,
                modes = modes,
                canResetPassword = false,
                backdropUrl = null,
            ),
        form = form,
        isConnecting = false,
        link = null,
        error = null,
        notice = null,
    )
}
