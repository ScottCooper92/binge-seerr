package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.ConnectionHealth
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.ui.hub.HubActions
import com.cooper.binge.companion.seerr.ui.hub.HubDownload
import com.cooper.binge.companion.seerr.ui.hub.HubOverview
import com.cooper.binge.companion.seerr.ui.hub.HubQuota
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.hub.HubScreen
import com.cooper.binge.companion.seerr.ui.hub.HubServer
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubUserLoad
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hub at its worst: a long server name and account, an update behind, every manage row, metered quotas and a
 * download with a long title, at font scales 1.3 and 2.0, and each connection problem, through the design system's
 * `assertNoTextClipped`. Native graphics, because legacy graphics measures text at a pixel a character.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h8000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(
        fontScale: Float,
        state: HubUiState,
    ) = rule.assertNoClippingAt(fontScale) { HubScreen(state = state, actions = noActions()) }

    @Test fun `a full hub at 1_3`() = check(FONT_SCALE_13, worstCaseHub())

    @Test fun `a full hub at 2_0`() = check(FONT_SCALE_20, worstCaseHub())

    @Test fun `an unreachable hub at 1_3`() = check(FONT_SCALE_13, worstCaseHub(ConnectionHealth.Unreachable))

    @Test fun `an unreachable hub at 2_0`() = check(FONT_SCALE_20, worstCaseHub(ConnectionHealth.Unreachable))

    @Test fun `a hub denied the local network at 2_0`() = check(FONT_SCALE_20, worstCaseHub(ConnectionHealth.LocalNetworkDenied))

    @Test fun `a hub that could not load at 2_0`() = check(FONT_SCALE_20, worstCaseHub(ConnectionHealth.CouldNotLoad))

    @Test fun `a first read that failed at 2_0`() = check(FONT_SCALE_20, HubUiState.Error(ConnectionHealth.Unreachable))

    @Test fun `a hub offering Binge at 2_0`() = check(FONT_SCALE_20, worstCaseHub(binge = BingeStatus.NotInstalled))

    @Test fun `a hub with Binge hint at 2_0`() = check(FONT_SCALE_20, worstCaseHub(binge = BingeStatus.Connected))

    private fun worstCaseHub(
        health: ConnectionHealth = ConnectionHealth.Healthy,
        binge: BingeStatus = BingeStatus.NotConnected,
    ) = HubUiState.Ready(
        server =
            HubServer(
                baseUrl = "https://a-rather-long-hostname.living-room.example.org:5055",
                title = "Alexandria Fitzgerald-Montgomery's Front Room Jellyseerr",
                variant = SeerrVariant.Seerr,
                versionLabel = "3.1.0-develop.2026.10.12",
                updateAvailable = true,
                commitsBehind = 1_204,
            ),
        health = health,
        overview =
            HubOverview(
                loaded = true,
                userLoad = HubUserLoad.Loaded,
                account = HubAccount(id = 1, name = LONG_NAME, isAdmin = true, avatarUrl = null),
                quota =
                    HubQuota(
                        movie = HubQuotaBucket(limit = 10_000, remaining = 0, days = 365),
                        tv = HubQuotaBucket(limit = 8_000, remaining = 5_000, days = 365),
                    ),
                permissions = SeerrPermissions(isAdmin = true),
                movieRequestCount = 12_840,
                tvRequestCount = 9_471,
                pendingRequestCount = 1_204,
                openIssueCount = 318,
                userCount = 2_048,
                blocklistCount = 4_096,
                hasIssues = true,
                hasBlocklist = true,
            ),
        downloading =
            listOf(
                HubDownload(
                    requestId = 1,
                    title = LONG_TITLE,
                    posterUrl = null,
                    fraction = 0.42f,
                    totalBytes = 46_000_000_000_000L,
                    etaMinutes = 1_440,
                ),
            ),
        bingeStatus = binge,
    )

    private fun noActions() =
        HubActions(
            onOpenSection = {},
            onOpenAccount = {},
            onOpenRequest = {},
            onRetry = {},
            onReconnect = {},
            onDisconnect = {},
            onDismissBingeHint = {},
            onRefresh = {},
        )
}
