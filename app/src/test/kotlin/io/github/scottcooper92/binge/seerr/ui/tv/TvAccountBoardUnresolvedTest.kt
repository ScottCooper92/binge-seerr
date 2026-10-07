package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvAccountBoard
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The account page when the hub could not resolve the account: a retry, not a spinner with no way out. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvAccountBoardUnresolvedTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    @Test
    fun aFailedAccountOffersRetry() {
        var retries = 0
        composeTestRule.setContent {
            BingeTvTheme {
                TvAccountBoard(
                    detail = null,
                    requests = TvPagedRows(count = 0, at = { null }),
                    onOpenRequest = {},
                    onRetry = { retries++ },
                    overlayOpen = false,
                    accountFailed = true,
                )
            }
        }

        composeTestRule.onNode(hasText("Retry", ignoreCase = true)).assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }
}
