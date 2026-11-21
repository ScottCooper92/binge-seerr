package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The phone sign-in step's modes are pages: a swipe and a tap on a tab both change the mode, and the typed text stays. */
@RunWith(RobolectricTestRunner::class)
class SetupSignInPagerTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var form by mutableStateOf(SignInForm(mode = SeerrSignInMode.Local))

    private fun show() =
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                SetupScreen(
                    state = signIn(form),
                    actions =
                        SetupActions(
                            onEditAddress = {},
                            onInspect = {},
                            onChangeServer = {},
                            onEditForm = { edit -> form = form.edit() },
                            onConnect = {},
                            onPlexLaunched = {},
                            onCancelLink = {},
                            onRequestPasswordReset = {},
                        ),
                    title = null,
                    onBack = null,
                    onReportProblem = null,
                )
            }
        }

    private fun pager() = rule.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasTestTag(SIGN_IN_PAGE_TAG)))

    @Test
    fun `swiping to the next page changes the mode`() {
        show()

        pager().performScrollToIndex(1)
        rule.waitForIdle()

        assertEquals(SeerrSignInMode.Jellyfin, form.mode)
        rule.onNodeWithText("Username").assertExists()
    }

    @Test
    fun `tapping a tab pages to its mode`() {
        show()

        rule.onNode(hasText("Jellyfin")).performClick()
        rule.waitForIdle()

        assertEquals(SeerrSignInMode.Jellyfin, form.mode)
        rule.onNodeWithText("Username").assertExists()
    }

    @Test
    fun `text typed on a page survives a swipe away and back`() {
        show()
        rule.onNodeWithText("Email").performTextInput("me@example.com")

        pager().performScrollToIndex(1)
        rule.waitForIdle()
        rule.onNodeWithText("Username").assertExists()
        pager().performScrollToIndex(0)
        rule.waitForIdle()

        assertEquals(SeerrSignInMode.Local, form.mode)
        rule.onNodeWithText("Email").assertTextContains("me@example.com")
    }

    private fun signIn(form: SignInForm) =
        SetupUiState.SignIn(
            server =
                SetupServer(
                    baseUrl = "http://seerr.lan:5055",
                    title = "Living room",
                    variant = SeerrVariant.Jellyseerr,
                    versionLabel = "2.7.2",
                    mediaServerName = null,
                    modes = listOf(SeerrSignInMode.Local, SeerrSignInMode.Jellyfin, SeerrSignInMode.QuickConnect),
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
