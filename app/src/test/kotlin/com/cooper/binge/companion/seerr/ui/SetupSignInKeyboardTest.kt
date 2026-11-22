package com.cooper.binge.companion.seerr.ui

import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * With the keyboard up on a small phone, the sign-in's focused field is whole, below the tabs and above the pinned button. The
 * backdrop and heading step aside for it, so the pager has the room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class SetupSignInKeyboardTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show(fontScale: Float) =
        rule.setContent {
            WithKeyboard(KEYBOARD) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    BingeExpressiveTheme(dynamicColor = false) {
                        SetupScreen(
                            state = signIn(),
                            actions =
                                SetupActions(
                                    onEditAddress = {},
                                    onInspect = {},
                                    onChangeServer = {},
                                    onEditForm = {},
                                    onConnect = {},
                                    onPlexLaunched = {},
                                    onCancelLink = {},
                                    onRequestPasswordReset = {},
                                ),
                        )
                    }
                }
            }
        }

    private fun assertFieldClear() {
        rule.onNodeWithText("Username").performTextInput("scott")
        rule.waitForIdle()

        val tab = rule.onNodeWithText("Jellyfin").getUnclippedBoundsInRoot()
        val field = rule.onNodeWithText("Username").getUnclippedBoundsInRoot()
        val button = rule.onNodeWithText("Sign in").getUnclippedBoundsInRoot()
        assertTrue("the field ${field.top} is under the tabs ${tab.bottom}", field.top >= tab.bottom)
        assertTrue("the field ${field.bottom} runs behind the button ${button.top}", field.bottom <= button.top)
    }

    @Test
    fun `the focused field is clear of the tabs and the button at 1x`() {
        show(fontScale = 1f)

        assertFieldClear()
    }

    @Test
    fun `the focused field is clear of the tabs and the button at 2x`() {
        show(fontScale = 2f)

        assertFieldClear()
    }

    private fun signIn() =
        SetupUiState.SignIn(
            server =
                SetupServer(
                    baseUrl = "http://seerr.lan:5055",
                    title = "Living room",
                    variant = SeerrVariant.Jellyseerr,
                    versionLabel = "2.7.2",
                    mediaServerName = null,
                    modes = listOf(SeerrSignInMode.Local, SeerrSignInMode.Jellyfin),
                    canResetPassword = false,
                    backdropUrl = null,
                ),
            form = SignInForm(mode = SeerrSignInMode.Jellyfin),
            isConnecting = false,
            link = null,
            error = null,
            notice = null,
        )

    private companion object {
        val KEYBOARD = 260.dp
    }
}

/** Opens a keyboard [height] tall over the window, answering every insets dispatch at the parent. */
@Composable
internal fun WithKeyboard(
    height: Dp,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val px = with(LocalDensity.current) { height.roundToPx() }
    // Read first, so Compose's own insets listener is installed on the view before the dispatch below.
    WindowInsets.systemBars
    DisposableEffect(view, px) {
        val host = view.parent as View
        val platform =
            checkNotNull(
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build()
                    .toWindowInsets(),
            )
        host.setOnApplyWindowInsetsListener { _, _ -> platform }
        host.dispatchApplyWindowInsets(platform)
        onDispose { host.setOnApplyWindowInsetsListener(null) }
    }
    content()
}
