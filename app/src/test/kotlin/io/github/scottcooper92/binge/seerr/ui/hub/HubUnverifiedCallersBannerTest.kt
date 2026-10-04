package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A debug build says on the hub that it admits Binge's package names under any certificate (#679). */
@RunWith(RobolectricTestRunner::class)
class HubUnverifiedCallersBannerTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val banner =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.hub_unverified_callers)

    private val ready =
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
            overview = HubOverview(loaded = true),
            downloading = emptyList(),
            bingeStatus = BingeStatus.Connected,
        )

    private fun show(admitsUnverifiedCallers: Boolean) =
        rule.setContent {
            SeerrTheme {
                HubScreen(
                    state = ready,
                    actions =
                        HubActions(
                            onOpenSection = {},
                            onOpenAccount = {},
                            onOpenRequest = {},
                            onRetry = {},
                            onReconnect = {},
                            onDisconnect = {},
                            onDismissBingeHint = {},
                        ),
                    admitsUnverifiedCallers = admitsUnverifiedCallers,
                )
            }
        }

    @Test
    fun `a build that admits unverified callers shows the banner`() {
        show(admitsUnverifiedCallers = true)

        rule.onNode(hasText(banner)).assertExists()
    }

    @Test
    fun `a pinned build shows no banner`() {
        show(admitsUnverifiedCallers = false)

        rule.onNode(hasText(banner)).assertDoesNotExist()
    }
}
