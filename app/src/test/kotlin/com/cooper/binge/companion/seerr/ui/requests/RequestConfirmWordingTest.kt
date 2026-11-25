package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

@RunWith(RobolectricTestRunner::class)
class RequestConfirmWordingTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `a decline where the viewer cannot block does not say the title is blocked`() {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                RequestConfirm(
                    removing = false,
                    removeMessage = "",
                    onConfirm = {},
                    onStep = {},
                    blockTitle = false,
                    onBlockTitleChange = null,
                )
            }
        }
        composeTestRule.onNodeWithText(string(AdminR.string.request_decline_confirm_title)).assertExists()
        composeTestRule.onNodeWithText(string(AdminR.string.request_decline_block_confirm_title)).assertDoesNotExist()
    }
}
