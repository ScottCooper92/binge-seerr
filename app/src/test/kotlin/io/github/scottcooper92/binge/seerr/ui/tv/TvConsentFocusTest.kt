package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * At a large font the consent page is taller than the window (#1258). The answer still takes the arrival focus and is
 * in view, and the D-pad walks up through every point, each brought into view in turn, so nothing is out of reach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvConsentFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    @Test
    fun aPageTallerThanTheWindowStillReachesEveryPoint() {
        RuntimeEnvironment.setFontScale(LARGE_FONT)
        composeTestRule.setContent { BingeTvTheme { TvConsentScreen(onChoice = {}) } }
        composeTestRule.waitForIdle()

        focusable(R.string.consent_accept).assertIsFocused().assertIsDisplayed()

        listOf(R.string.tv_consent_point_change_title, R.string.consent_point_never_title, R.string.consent_point_shared_title)
            .forEach { title ->
                press(Key.DirectionUp)
                focusable(title).assertIsFocused().assertIsDisplayed()
            }
    }

    private fun focusable(id: Int) = composeTestRule.onNode(hasText(string(id), substring = true) and isFocusable())

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val LARGE_FONT = 1.5f
    }
}
