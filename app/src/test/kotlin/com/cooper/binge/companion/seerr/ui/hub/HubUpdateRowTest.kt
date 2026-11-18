package com.cooper.binge.companion.seerr.ui.hub

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The server card's update row is a button that opens the project's release notes, and only when the server reports an
 * update. The app itself makes no request: the browser the tap opens does.
 */
@RunWith(RobolectricTestRunner::class)
class HubUpdateRowTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val row = hasText("Update available") and hasText("Release notes") and hasClickAction()

    /** The card as the server reports itself; [variant] is state, so a test can move through the projects in one composition. */
    private fun show(
        variant: MutableState<SeerrVariant>,
        updateAvailable: Boolean,
    ) = rule.setContent {
        SeerrTheme {
            HubScreen(
                state =
                    readyHub(HubOverview(loaded = true, permissions = SeerrPermissions()))
                        .let { it.copy(server = it.server.copy(variant = variant.value, updateAvailable = updateAvailable)) },
                actions =
                    HubActions(
                        onOpenSection = {},
                        onOpenAccount = {},
                        onOpenRequest = {},
                        onRetry = {},
                        onReconnect = {},
                        onDisconnect = {},
                        onDismissBingeHint = {},
                        onRefresh = {},
                    ),
            )
        }
    }

    private fun openedUrl(): String? = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity?.dataString

    @Test
    fun `the update row opens each project's release notes`() {
        val expected =
            mapOf(
                SeerrVariant.Seerr to "https://github.com/seerr-team/seerr/releases",
                // Jellyseerr's project moved into Seerr's, and its old address redirects there.
                SeerrVariant.Jellyseerr to "https://github.com/seerr-team/seerr/releases",
                SeerrVariant.Overseerr to "https://github.com/sct/overseerr/releases",
                SeerrVariant.Unknown to "https://github.com/seerr-team/seerr/releases",
            )
        val shown = mutableStateOf(SeerrVariant.Seerr)
        show(shown, updateAvailable = true)
        expected.forEach { (variant, url) ->
            shown.value = variant
            rule.waitForIdle()
            rule.onNode(row).performClick()
            assertEquals(variant.name, url, openedUrl())
        }
    }

    @Test
    fun `a current server's card has no update row`() {
        show(mutableStateOf(SeerrVariant.Seerr), updateAvailable = false)

        rule.onAllNodes(row).assertCountEquals(0)
    }
}
