package com.cooper.binge.companion.seerr.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.FieldKind
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInActions
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Seerr's words and parts in the shared sign-in step: the same labels, notes and notice the setup form shows today. */
@RunWith(RobolectricTestRunner::class)
class SeerrConnectPartsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var resets = 0
    private val edits = mutableListOf<SignInForm.() -> SignInForm>()

    private fun server(
        modes: List<SeerrSignInMode>,
        canResetPassword: Boolean = false,
        mediaServerName: String? = null,
    ): ProbedServer<SetupServer> {
        val setup =
            SetupServer(
                baseUrl = "https://seerr.example.com/",
                title = "Living room",
                variant = SeerrVariant.Jellyseerr,
                versionLabel = "2.7.2",
                mediaServerName = mediaServerName,
                modes = modes,
                canResetPassword = canResetPassword,
                backdropUrl = null,
            )
        return ProbedServer(
            address = requireNotNull(ServerAddress.parse("https://seerr.example.com/")),
            name = setup.title,
            setUp = true,
            modes = modes.mapNotNull { it.formMode() },
            preview = setup,
        )
    }

    private fun show(
        server: ProbedServer<SetupServer>,
        mode: SeerrSignInMode,
        values: Map<String, String> = emptyMap(),
        error: ConnectError? = null,
        notice: SetupNotice? = null,
        resetCoolingDown: Boolean = false,
    ) {
        val state = ConnectState.SignIn(server, SignInForm(mode = mode.formMode(), values = values), isConnecting = false, error = error)
        val options = seerrSignInOptions(notice, resetCoolingDown, { resets++ }, { edits += it })
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                ConnectSignInContent(
                    state = state,
                    actions = ConnectSignInActions({}, { edits += it }, {}, {}, {}, {}),
                    copy = seerrConnectCopy(),
                    errorText = ::seerrErrorText,
                    afterFields = options.afterFields,
                    afterError = options.afterError,
                    fieldModifier = options.fieldModifier,
                )
            }
        }
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Test
    fun `the api key carries its hint and the note about the administrator's reach`() {
        show(server(listOf(SeerrSignInMode.ApiKey)), SeerrSignInMode.ApiKey)

        rule.onNode(hasText(string(R.string.setup_api_key)) and hasSetTextAction()).assertExists()
        rule.onNodeWithText(string(R.string.setup_api_key_hint)).assertExists()
        rule.onNodeWithText(string(R.string.setup_api_key_admin_note)).assertExists()
    }

    @Test
    fun `a local account offers the reset only once an email is typed`() {
        val local = server(listOf(SeerrSignInMode.Local), canResetPassword = true)
        show(local, SeerrSignInMode.Local)
        rule.onNodeWithText(string(R.string.setup_forgot_password)).assertIsNotEnabled()
    }

    @Test
    fun `the reset runs with an email typed and waits out its cooldown`() {
        val local = server(listOf(SeerrSignInMode.Local), canResetPassword = true)
        show(local, SeerrSignInMode.Local, values = mapOf(FIELD_EMAIL to "ana@example.com"))
        rule.onNodeWithText(string(R.string.setup_forgot_password)).assertIsEnabled().performClick()
        assertEquals(1, resets)
    }

    @Test
    fun `the reset is disabled while the last one cools down`() {
        val local = server(listOf(SeerrSignInMode.Local), canResetPassword = true)
        show(local, SeerrSignInMode.Local, values = mapOf(FIELD_EMAIL to "ana@example.com"), resetCoolingDown = true)
        rule.onNodeWithText(string(R.string.setup_forgot_password)).assertIsNotEnabled()
    }

    @Test
    fun `a server that cannot reset a password offers no reset`() {
        show(server(listOf(SeerrSignInMode.Local)), SeerrSignInMode.Local)
        rule.onNodeWithText(string(R.string.setup_forgot_password)).assertDoesNotExist()
    }

    @Test
    fun `the jellyfin tab and its username placeholder carry the server's own name`() {
        val jellyfin = server(listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.Local), mediaServerName = "Den")
        show(jellyfin, SeerrSignInMode.Jellyfin)

        rule.onNodeWithText("Den").assertExists()
        rule.onNodeWithText(string(R.string.setup_mode_local)).assertExists()
        rule.onNodeWithText(string(R.string.setup_username)).assertExists()
    }

    @Test
    fun `a refused sign-in reads as the rejected line and a notice follows it`() {
        show(
            server(listOf(SeerrSignInMode.Local)),
            SeerrSignInMode.Local,
            error = ConnectError.Failed(ServerFailure.SessionRejected),
            notice = SetupNotice.SessionRejected,
        )

        rule.onNodeWithText(string(SetupError.Rejected.messageRes())).assertExists()
        rule.onNode(hasText(string(SetupNotice.SessionRejected.messageRes()))).assertExists()
    }

    @Test
    fun `the api key field asks the platform for no password type`() {
        assertEquals(
            FieldKind.Token,
            SeerrSignInMode.ApiKey
                .formMode()
                ?.fields
                ?.single()
                ?.kind,
        )
    }
}
