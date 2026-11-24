package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.state.DownloadActivity
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestItem
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/** A sheet taller than its host scrolls, so its last row is never cut off. */
@RunWith(RobolectricTestRunner::class)
class RequestActionsContentScrollTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `in a short host the last row can be scrolled fully into view`() {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                Box(Modifier.height(SHORT_HOST)) {
                    RequestActionsContent(
                        model =
                            RequestSheetModel(
                                item = requestItem(id = 11, mediaType = RequestMediaType.Tv, title = "Heart of the Beast", status = null),
                                actions = RequestActions(),
                                media =
                                    MediaRecord(
                                        mediaId = 900,
                                        isTv = true,
                                        instances =
                                            listOf(
                                                MediaInstance(
                                                    is4k = false,
                                                    status = SeerrMediaStatusCode.Available,
                                                    serviceUrl = null,
                                                    mediaServerUrl = null,
                                                    watch = null,
                                                    download = DownloadActivity.None,
                                                ),
                                                MediaInstance(
                                                    is4k = true,
                                                    status = SeerrMediaStatusCode.Available,
                                                    serviceUrl = null,
                                                    mediaServerUrl = null,
                                                    watch = null,
                                                    download = DownloadActivity.None,
                                                ),
                                            ),
                                        canSetStatus = true,
                                        canClearData = true,
                                        canDeleteFiles = false,
                                    ),
                            ),
                        callbacks = RequestSheetCallbacks(onApprove = {}, onRetry = {}, onDecline = {}, onRemove = {}, onDeleteFiles = {}),
                        blockTitle = false,
                        onBlockTitleChange = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val last = composeTestRule.onNodeWithText(string(AdminR.string.media_clear_data))
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(string(AdminR.string.media_clear_data)))
        last.assertIsDisplayed()
    }

    private companion object {
        val SHORT_HOST = 260.dp
    }
}
