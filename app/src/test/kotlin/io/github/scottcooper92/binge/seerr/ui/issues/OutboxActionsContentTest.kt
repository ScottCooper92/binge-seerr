package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A pending comment's sheet turns Edit and Discard off while its send is in flight (#1028). */
@RunWith(RobolectricTestRunner::class)
class OutboxActionsContentTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `edit and discard are off while the send is in flight`() {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                OutboxActionsContent(retryable = false, sending = true, onRetry = {}, onEdit = {}, onDrop = {})
            }
        }

        rule.onNodeWithText("Edit comment").assertIsNotEnabled()
        rule.onNodeWithText("Discard").assertIsNotEnabled()
    }

    @Test
    fun `edit and discard are on once the send has failed`() {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                OutboxActionsContent(retryable = true, sending = false, onRetry = {}, onEdit = {}, onDrop = {})
            }
        }

        rule.onNodeWithText("Edit comment").assertIsEnabled()
        rule.onNodeWithText("Discard").assertIsEnabled()
        rule.onNodeWithText("Retry").assertIsEnabled()
    }
}
