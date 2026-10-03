package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.BingeStatus
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvBingeTile
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Binge tile on the television hub (#475): a missing Binge is a card the remote selects, and the
 * other two states are hints with nothing to focus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvBingeTileTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private var opened = 0

    private fun setTile(status: BingeStatus) {
        composeTestRule.setContent { BingeTvTheme { TvBingeTile(status = status, onOpenPlayStore = { opened++ }) } }
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun aMissingBingeIsACardThatSelectOpensTheListingFrom() {
        setTile(BingeStatus.NotInstalled)

        composeTestRule.onNodeWithText(string(R.string.hub_binge_not_installed_title), substring = true).assertExists()
        composeTestRule.onNode(isFocusable()).requestFocus().assertIsFocused()
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(1, opened)
    }

    @Test
    fun anInstalledBingeIsAHintWithNothingToFocus() {
        setTile(BingeStatus.NotConnected)

        composeTestRule.onNode(hasText(string(R.string.connected_hint))).assertExists()
        composeTestRule.onAllNodes(isFocusable()).assertCountEquals(0)
    }

    @Test
    fun aConnectedBingeIsAHintWithNothingToFocus() {
        setTile(BingeStatus.Connected)

        composeTestRule.onNode(hasText(string(R.string.hub_binge_connected_hint))).assertExists()
        composeTestRule.onAllNodes(isFocusable()).assertCountEquals(0)
    }
}
