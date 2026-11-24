package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.uiandroid.connect.ConnectAddressActions
import com.cooper.homelab.core.uiandroid.connect.ConnectLinkActions
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/**
 * Fast typing into every setup field loses no character (core #273). A view model reads an edit and writes the state back on another
 * thread, so the screen is sent each character before the state has come back from the one before, as `adb input text` and a fast
 * keyboard do. What the view model ends up holding is what was typed.
 */
@RunWith(RobolectricTestRunner::class)
class SeerrConnectTypingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow<ConnectState<SetupServer>>(address())

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    /** The state written back off the main thread after a short wait, as a view model does. */
    private fun roundTrip(change: (ConnectState<SetupServer>) -> ConnectState<SetupServer>) {
        scope.launch {
            delay(ROUND_TRIP_MILLIS)
            state.update(change)
        }
    }

    private fun show(title: String? = null) =
        rule.setContent {
            val current by state.collectAsState()
            BingeExpressiveTheme(dynamicColor = false) {
                SeerrConnectContent(
                    ui = SeerrConnectUi(current),
                    addressActions =
                        ConnectAddressActions(
                            { text -> roundTrip { (it as? ConnectState.Address)?.copy(address = text) ?: it } },
                            {},
                            {},
                            {},
                        ),
                    signInActions =
                        ConnectSignInActions(
                            {},
                            { transform ->
                                roundTrip {
                                    (it as? ConnectState.SignIn)?.let { s ->
                                        s.copy(form = s.form.transform())
                                    } ?: it
                                }
                            },
                            {},
                            {},
                            {},
                            {},
                        ),
                    linkActions = ConnectLinkActions({}, {}),
                    onRequestPasswordReset = {},
                    title = title,
                    onExit = title?.let { {} },
                )
            }
        }

    private fun typeInBursts(
        field: androidx.compose.ui.test.SemanticsNodeInteraction,
        text: String,
    ) = text.forEach { field.performTextInput(it.toString()) }

    private fun awaitValue(
        id: String,
        expected: String,
    ) {
        rule.waitUntil(SETTLE_MILLIS) { (state.value as? ConnectState.SignIn)?.form?.value(id) == expected }
        assertEquals(expected, (state.value as ConnectState.SignIn).form.value(id))
    }

    @Test
    fun `fast typing into the address loses nothing`() {
        show()

        typeInBursts(rule.onAllNodes(hasSetTextAction())[0], ADDRESS)

        rule.waitUntil(SETTLE_MILLIS) { (state.value as ConnectState.Address).address == ADDRESS }
        assertEquals(ADDRESS, (state.value as ConnectState.Address).address)
    }

    @Test
    fun `fast typing into the username loses nothing`() {
        state.value = signIn(SeerrSignInMode.Jellyfin)
        show()

        typeInBursts(rule.onNode(hasSetTextAction() and hasText(string(AdminR.string.setup_username))), USERNAME)

        awaitValue(FIELD_USERNAME, USERNAME)
    }

    @Test
    fun `fast typing into the password loses nothing`() {
        state.value = signIn(SeerrSignInMode.Local)
        show()

        typeInBursts(rule.onNode(hasSetTextAction() and hasText(string(AdminR.string.setup_password))), PASSWORD)

        awaitValue(FIELD_PASSWORD, PASSWORD)
    }

    @Test
    fun `fast typing into the email loses nothing`() {
        state.value = signIn(SeerrSignInMode.Local)
        show()

        typeInBursts(rule.onNode(hasSetTextAction() and hasText(string(AdminR.string.setup_email))), EMAIL)

        awaitValue(FIELD_EMAIL, EMAIL)
    }

    @Test
    fun `fast typing into the api key loses nothing`() {
        state.value = signIn(SeerrSignInMode.ApiKey)
        show()

        typeInBursts(rule.onNode(hasSetTextAction() and hasText(string(R.string.setup_api_key))), API_KEY)

        awaitValue(FIELD_API_KEY, API_KEY)
    }

    @Test
    fun `fast typing into the username loses nothing on the titled Edit connection screen`() {
        state.value = signIn(SeerrSignInMode.Jellyfin)
        show(title = "Edit connection")

        rule.onNodeWithText("Edit connection").assertExists()
        typeInBursts(rule.onNode(hasSetTextAction() and hasText(string(AdminR.string.setup_username))), USERNAME)

        awaitValue(FIELD_USERNAME, USERNAME)
    }

    @Test
    fun `fast typing into the address loses nothing on the titled Edit connection screen`() {
        show(title = "Edit connection")

        typeInBursts(rule.onAllNodes(hasSetTextAction())[0], ADDRESS)

        rule.waitUntil(SETTLE_MILLIS) { (state.value as ConnectState.Address).address == ADDRESS }
        assertEquals(ADDRESS, (state.value as ConnectState.Address).address)
    }

    private fun address(): ConnectState<SetupServer> =
        ConnectState.Address(
            address = "",
            insecure = false,
            cleartextAllowed = false,
            isInspecting = false,
            error = null,
            needsLocalNetwork = false,
            canTryDemo = false,
        )

    private fun signIn(mode: SeerrSignInMode): ConnectState<SetupServer> {
        val modes = listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.Local, SeerrSignInMode.ApiKey)
        val server =
            SetupServer(
                baseUrl = "http://192.168.86.38:30044",
                title = "Seerr test",
                variant = SeerrVariant.Jellyseerr,
                versionLabel = "2.7.2",
                mediaServerName = null,
                modes = modes,
                canResetPassword = false,
                backdropUrl = null,
            )
        val probed =
            ProbedServer(
                address = requireNotNull(ServerAddress.parse(server.baseUrl)),
                name = server.title,
                setUp = true,
                modes = modes.mapNotNull { it.formMode() },
                preview = server,
            )
        return ConnectState.SignIn(probed, SignInForm(mode = mode.formMode()), isConnecting = false, error = null)
    }

    private companion object {
        const val ADDRESS = "http://192.168.86.38:30044"
        const val USERNAME = "scottcooper92"
        const val PASSWORD = "correct-horse-battery-staple"
        const val EMAIL = "scott.cooper@example.com"
        const val API_KEY = "MTc0NDE1NzQ0MjQyMzFhYzY0OTk5"
        const val ROUND_TRIP_MILLIS = 15L
        const val SETTLE_MILLIS = 10_000L
    }
}
