package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.sheetDetailLoad
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A sheet opened from a preview says whether its detail is loading or failed, instead of growing silently (#565). */
@RunWith(RobolectricTestRunner::class)
class RequestSheetDetailLoadTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val resources get() = ApplicationProvider.getApplicationContext<android.app.Application>().resources

    @Test
    fun `the page state maps to the sheet's detail state`() {
        assertEquals(SheetDetailLoad.Loading, RequestDetailUiState.Loading.sheetDetailLoad())
        assertEquals(
            SheetDetailLoad.Failed(SeerrError.Unreachable),
            RequestDetailUiState.Error(SeerrError.Unreachable).sheetDetailLoad(),
        )
    }

    @Test
    fun `loading shows the bar and no retry`() {
        setContent(SheetDetailLoad.Loading)

        composeTestRule.onNodeWithContentDescription(resources.getString(R.string.request_sheet_loading)).assertExists()
        composeTestRule.onAllRetry().assertCountEquals(0)
    }

    @Test
    fun `a failed load stops the bar and offers a retry`() {
        var retries = 0
        setContent(SheetDetailLoad.Failed(SeerrError.Unreachable), onRetryDetail = { retries++ })

        composeTestRule.onNodeWithContentDescription(resources.getString(R.string.request_sheet_loading)).assertDoesNotExist()
        composeTestRule.onNodeWithText(resources.getString(R.string.action_try_again)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `a loaded detail shows neither`() {
        setContent(SheetDetailLoad.Loaded)

        composeTestRule.onNodeWithContentDescription(resources.getString(R.string.request_sheet_loading)).assertDoesNotExist()
        composeTestRule.onAllRetry().assertCountEquals(0)
    }

    private fun ComposeContentTestRule.onAllRetry() = onAllNodesWithText(resources.getString(R.string.action_try_again))

    private fun setContent(
        load: SheetDetailLoad,
        onRetryDetail: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                RequestActionsContent(
                    model = RequestSheetModel(item = item(), actions = RequestActions(), detailLoad = load),
                    callbacks =
                        RequestSheetCallbacks(
                            onApprove = {},
                            onRetry = {},
                            onDecline = {},
                            onRemove = {},
                            onRetryDetail = onRetryDetail,
                        ),
                    blockTitle = false,
                    onBlockTitleChange = {},
                )
            }
        }
    }

    private fun item() =
        RequestItem(
            id = 11,
            tmdbId = 1396,
            mediaType = RequestMediaType.Tv,
            title = "Breaking Bad",
            posterUrl = null,
            year = "2008",
            requestedBy = null,
            requestedById = null,
            requestedAtMillis = null,
            status = null,
            mediaStatus = null,
            download = null,
            seasonNumbers = emptyList(),
            is4k = false,
        )
}
