package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupNotice
import io.github.scottcooper92.binge.seerr.ui.SetupServer
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.SignInForm
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val SERVER = "Living room"

/**
 * The code page's own rules, past where the remote lands (#1054): the listener stops when the app does, Disconnect is
 * only there for a session the server rejected, and the line under the code follows what the TV is doing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvSetupCodePageTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var cancelledHandOff = 0
    private var disconnected = 0

    /** The page's own lifecycle, so a test can stop it without stopping the activity the rule tears down. */
    private val lifecycle = TestLifecycle()

    private val code = AddressHandOff.Listening("http://192.168.1.20:41234/a/k7m2pqx4")

    @Test
    fun leavingTheAppStopsTheAddressStepsListener() {
        setPage(SetupUiState.Address(serverUrl = "", insecure = false, isInspecting = false, error = null, handOff = code, code = code))

        assertEquals(0, cancelledHandOff)
        composeTestRule.runOnUiThread { lifecycle.registry.currentState = Lifecycle.State.CREATED }
        composeTestRule.waitForIdle()

        assertEquals(1, cancelledHandOff)
    }

    @Test
    fun theLineUnderTheCodeSaysWhenTheTvIsCheckingTheAddress() {
        setPage(SetupUiState.Address(serverUrl = "http://seerr.lan:5055", insecure = false, isInspecting = true, error = null, code = code))

        composeTestRule.onNodeWithText(string(R.string.tv_setup_status_checking)).assertExists()
    }

    @Test
    fun theLineUnderTheCodeNamesTheServerWhileSigningIn() {
        setPage(signIn().copy(isConnecting = true, code = code))

        composeTestRule.onNodeWithText(string(R.string.tv_setup_status_signing_in, SERVER)).assertExists()
    }

    @Test
    fun aSignInTheAppDidNotPutUpOffersNoDisconnect() {
        setPage(signIn().copy(code = code))

        composeTestRule.onNodeWithText(string(R.string.tv_setup_code_headline)).assertExists()
        button(R.string.hub_disconnect).assertDoesNotExist()
    }

    /** #810: the server rejected the saved session, so the page says so and offers leaving the server. */
    @Test
    fun aRejectedSessionSaysSoAndOffersDisconnect() {
        setPage(signIn().copy(code = code, notice = SetupNotice.SessionRejected), onDisconnect = { disconnected++ })

        composeTestRule.onNodeWithText(string(R.string.hub_unauthorized_headline)).assertExists()
        button(R.string.hub_disconnect).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, disconnected)
    }

    private fun setPage(
        state: SetupUiState,
        onDisconnect: (() -> Unit)? = null,
    ) {
        val actions =
            SetupActions(
                onEditAddress = {},
                onInspect = {},
                onChangeServer = {},
                onEditForm = {},
                onConnect = {},
                onPlexLaunched = {},
                onCancelLink = {},
                onRequestPasswordReset = {},
                onCancelHandOff = { cancelledHandOff++ },
                onDisconnect = onDisconnect,
            )
        composeTestRule.runOnUiThread { lifecycle.registry.currentState = Lifecycle.State.RESUMED }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                BingeTvTheme { TvSetupScreen(state = state, actions = actions, offerHandOff = true) }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun button(label: Int) =
        composeTestRule.onNode((hasText(string(label)) or hasAnyDescendant(hasText(string(label)))) and isFocusable())

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun signIn() =
        SetupUiState.SignIn(
            server =
                SetupServer(
                    baseUrl = "http://seerr.lan:5055",
                    title = SERVER,
                    variant = SeerrVariant.Jellyseerr,
                    versionLabel = "2.7.2",
                    mediaServerName = null,
                    modes = listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.Local),
                    canResetPassword = false,
                    backdropUrl = null,
                ),
            form = SignInForm(mode = SeerrSignInMode.Jellyfin),
            isConnecting = false,
            link = null,
            error = null,
            notice = null,
        )
}

private class TestLifecycle : LifecycleOwner {
    val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry
}
