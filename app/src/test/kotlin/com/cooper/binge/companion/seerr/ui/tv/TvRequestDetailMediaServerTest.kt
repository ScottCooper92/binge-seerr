package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.ModerationEvent
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The request page offers to open the title in the media server's TV app, named for the server, only when the app is here. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestDetailMediaServerTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var opened = 0

    private fun setContent(
        mediaServerName: String?,
        appInstalled: Boolean,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestDetailScreen(
                    state = RequestDetailUiState.Ready(requestDetail().copy(mediaServerName = mediaServerName)),
                    events = MutableSharedFlow<ModerationEvent>(),
                    actions =
                        TvRequestDetailActions(
                            onBack = {},
                            onRetry = {},
                            onOpenInBinge = null,
                            onOpenInMediaServer = if (appInstalled) ({ opened++ }) else null,
                            onApprove = {},
                            onRetryRequest = {},
                            onDecline = {},
                            onRemove = {},
                            onBlock = {},
                            onSetMediaStatus = { _, _, _ -> },
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun openIn(name: String) = string(R.string.open_in_named, name)

    private fun assertAbsent(label: String) {
        composeTestRule.onAllNodes(hasContentDescription(label) or hasText(label), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `with the app installed the page offers to open the title in it, by the server's name, and OK opens it`() {
        setContent(mediaServerName = "Jellyfin", appInstalled = true)

        val label = openIn("Jellyfin")
        val button =
            composeTestRule.onNode(isFocusable() and (hasContentDescription(label) or hasText(label) or hasAnyDescendant(hasText(label))))
        button.requestFocus()
        button.assertIsFocused()
        button.performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()

        assertEquals(1, opened)
    }

    @Test
    fun `without the app the page offers nothing`() {
        setContent(mediaServerName = "Jellyfin", appInstalled = false)

        assertAbsent(openIn("Jellyfin"))
    }

    /** A server whose media server is not known has no name to offer it under, so the page offers nothing. */
    @Test
    fun `without the media server's name the page offers nothing`() {
        setContent(mediaServerName = null, appInstalled = true)

        assertAbsent(openIn("Jellyfin"))
    }
}
