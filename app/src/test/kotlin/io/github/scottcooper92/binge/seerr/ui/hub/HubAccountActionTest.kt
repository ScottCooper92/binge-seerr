package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.binge.designsystem.theme.BingeSentiment
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The hub bar's account action (#1325): what it says, what colour its ring takes, and where it goes. */
@RunWith(RobolectricTestRunner::class)
class HubAccountActionTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val ana = HubAccount(id = 7, name = "Ana", isAdmin = false, avatarUrl = null)

    @Test
    fun `tapping the avatar opens the user's own page`() {
        var opened = 0
        set(quota = null) { opened++ }

        rule.onNodeWithContentDescription("Ana").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `it names the user and what is left of the tighter quota`() {
        set(quota = HubQuota(movie = HubQuotaBucket(limit = 7, remaining = 7, days = 7), tv = HubQuotaBucket(10, 2, null)))

        rule.onNodeWithContentDescription("Ana, 2 of 10 TV requests left").assertExists()
    }

    @Test
    fun `an unlimited account says so`() {
        set(quota = HubQuota(movie = null, tv = null))

        rule.onNodeWithContentDescription("Ana, unlimited requests").assertExists()
    }

    @Test
    fun `the ring is green under half used, yellow from half, then red from four fifths`() {
        assertEquals(BingeSentiment.Positive, quotaSentiment(0f))
        assertEquals(BingeSentiment.Positive, quotaSentiment(0.49f))
        assertEquals(BingeSentiment.Caution, quotaSentiment(0.5f))
        assertEquals(BingeSentiment.Caution, quotaSentiment(0.79f))
        assertEquals(BingeSentiment.Negative, quotaSentiment(0.8f))
        assertEquals(BingeSentiment.Negative, quotaSentiment(1f))
    }

    @Test
    fun `the tighter bucket is the one nearer its limit, and none is metered when both are unlimited`() {
        val quota = HubQuota(movie = HubQuotaBucket(limit = 5, remaining = 4, days = null), tv = HubQuotaBucket(8, 2, null))

        assertEquals(HubQuotaType.Tv, quota.tightest()?.type)
        assertNull(HubQuota(movie = null, tv = null).tightest())
    }

    @Test
    fun `a bucket with no limit to give reads as spent`() {
        assertEquals(1f, HubQuotaBucket(limit = 0, remaining = 0, days = null).usedFraction)
        assertEquals(0.6f, HubQuotaBucket(limit = 10, remaining = 4, days = null).usedFraction)
    }

    private fun set(
        quota: HubQuota?,
        onClick: () -> Unit = {},
    ) {
        rule.setContent { BingeExpressiveTheme(dynamicColor = false) { HubAccountAction(account = ana, quota = quota, onClick = onClick) } }
    }
}
