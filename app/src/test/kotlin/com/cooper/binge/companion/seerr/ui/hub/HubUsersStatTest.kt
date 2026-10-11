package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The server only counts users for a viewer who manages them, so nobody else is shown a count that never fills in (#788). */
@RunWith(RobolectricTestRunner::class)
class HubUsersStatTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show(permissions: SeerrPermissions) =
        rule.setContent {
            SeerrTheme {
                HubScreen(
                    state = readyHub(HubOverview(loaded = true, permissions = permissions, userCount = USER_COUNT)),
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

    @Test
    fun `a viewer who manages users sees the count`() {
        show(SeerrPermissions(canManageUsers = true))

        // The Users section row beside it names the count as well, so it is found at least once rather than exactly once.
        assertTrue(rule.onAllNodes(hasText("$USER_COUNT")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a viewer who cannot manage users sees no Users stat`() {
        show(SeerrPermissions(canRequestMovie = true, canRequestSeries = true))

        rule.onAllNodes(hasText("$USER_COUNT")).assertCountEquals(0)
    }
}

/** A connected hub showing [overview]. */
internal fun readyHub(overview: HubOverview) =
    HubUiState.Ready(
        server =
            HubServer(
                baseUrl = "https://seerr.example.org",
                title = "Seerr",
                variant = SeerrVariant.Seerr,
                versionLabel = null,
                updateAvailable = false,
                commitsBehind = 0,
            ),
        health = ConnectionHealth.Healthy,
        overview = overview,
        downloading = emptyList(),
        bingeStatus = BingeStatus.Connected,
    )

/** Distinct from every other figure on the hub, so finding it finds the Users stat. */
internal const val USER_COUNT = 97
