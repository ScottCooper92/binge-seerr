package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.state.DownloadActivity
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestItem
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Manage media's version row names a processing version by its queue, not by the bare status (#1719). */
@RunWith(RobolectricTestRunner::class)
class RequestActionsContentMediaRowTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `a read-only processing version with nothing queued says it is not downloading, and why`() {
        setContent(instance(DownloadActivity.None), canSetStatus = false)

        composeTestRule.onNodeWithText(string(R.string.media_state_not_downloading)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.media_state_not_downloading_detail)).assertExists()
        assertEquals(0, count(R.string.media_state_downloading))
    }

    @Test
    fun `a processing version that is transferring reads downloading, with no explanation`() {
        setContent(instance(DownloadActivity.Downloading), canSetStatus = false)

        composeTestRule.onNodeWithText(string(R.string.media_state_downloading)).assertExists()
        assertEquals(0, count(R.string.media_state_not_downloading_detail))
    }

    @Test
    fun `a picker's detail is its value, read through the download`() {
        setContent(instance(DownloadActivity.None), canSetStatus = true)

        composeTestRule.onNodeWithText(string(R.string.media_state_not_downloading)).assertExists()
        assertEquals(0, count(R.string.media_state_not_downloading_detail))
    }

    private fun count(id: Int): Int = composeTestRule.onAllNodesWithText(string(id)).fetchSemanticsNodes().size

    private fun instance(download: DownloadActivity) =
        MediaInstance(
            is4k = false,
            status = SeerrMediaStatusCode.Processing,
            serviceUrl = null,
            mediaServerUrl = null,
            watch = null,
            download = download,
        )

    private fun setContent(
        instance: MediaInstance,
        canSetStatus: Boolean,
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                RequestActionsContent(
                    model =
                        RequestSheetModel(
                            item = requestItem(id = REQUEST_ID, mediaType = RequestMediaType.Tv, title = "Breaking Bad", status = null),
                            actions = RequestActions(),
                            media =
                                MediaRecord(
                                    mediaId = MEDIA_ID,
                                    isTv = true,
                                    instances = listOf(instance),
                                    canSetStatus = canSetStatus,
                                    // Clearing is what puts a read-only version row on screen at all.
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
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val MEDIA_ID = 900
        const val REQUEST_ID = 11
    }
}
