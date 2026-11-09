package com.cooper.binge.companion.seerr.ui.requests

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.requestItem
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A removal whose block failed closes the request's page like any removal, but only once the retry is answered: the
 * dialog and the view model that runs the retry both leave with the page (#1890).
 */
@RunWith(RobolectricTestRunner::class)
class RequestDetailBlockRetryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val events = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)
    private val removed = requestItem(id = 5, title = "Sintel")
    private var backs = 0
    private val retried = mutableListOf<RequestItem>()

    private fun setContent() {
        rule.setContent {
            SeerrTheme {
                RequestDetailScreen(
                    state = RequestDetailUiState.Ready(requestDetail(item = removed)),
                    events = events,
                    actions =
                        RequestDetailActions(
                            onBack = { backs++ },
                            onRetry = {},
                            onReportIssue = { _, _ -> },
                            onDismissReport = {},
                            onApprove = {},
                            onRetryRequest = {},
                            onDecline = {},
                            onRemove = {},
                            onBlockTitle = { retried += it },
                            onStartEdit = {},
                            siblingSheet = {},
                            onOpenUser = {},
                            edit =
                                EditRequestActions(
                                    onToggleSeason = {},
                                    onSelectAllSeasons = {},
                                    onSelectServer = {},
                                    onSelectProfile = {},
                                    onSelectRootFolder = {},
                                    onToggleTag = {},
                                    onSave = {},
                                    onDismiss = {},
                                ),
                            media = ManageMediaActions(onSetStatus = { _, _, _ -> }, onClearData = {}, onDeleteFiles = { _, _, _ -> }),
                        ),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun removalWhoseBlockFailed() {
        events.tryEmit(ModerationEvent.RemovedButBlockFailed(removed))
        rule.waitForIdle()
    }

    @Test
    fun `the page stays for the dialog, and Not now leaves`() {
        setContent()

        removalWhoseBlockFailed()

        rule.onNodeWithText(context.getString(R.string.request_removed_block_failed_title)).assertIsDisplayed()
        assertEquals(0, backs)
        rule.onNodeWithText(context.getString(R.string.action_not_now)).performClick()
        assertEquals(1, backs)
        assertEquals(emptyList<RequestItem>(), retried)
    }

    @Test
    fun `Try again blocks first, and the page leaves when the block lands`() {
        setContent()
        removalWhoseBlockFailed()

        rule.onNodeWithText(context.getString(R.string.action_try_again)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(removed), retried)
        assertEquals(0, backs)
        events.tryEmit(ModerationEvent.Blocked)
        rule.waitForIdle()
        assertEquals(1, backs)
    }

    @Test
    fun `a plain removal still leaves at once`() {
        setContent()

        events.tryEmit(ModerationEvent.Removed)
        rule.waitForIdle()

        assertEquals(1, backs)
    }
}
