package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationScope
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSort
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsBoard
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val HEAT = "Heat"
private const val BEAR = "The Bear"

/**
 * The requests board under a real D-pad: the band to the rows, a row to its sheet, the sheet's first
 * action, and the way back. The rows come from a plain list decomposed the way the entry decomposes the
 * pager, so nothing here waits on paging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi", application = android.app.Application::class)
class TvRequestsBoardFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val approved = mutableListOf<Int>()
    private val declined = mutableListOf<Pair<Int, Boolean>>()
    private var dismissed = 0
    private var filters = mutableListOf<RequestFilter>()

    private val manager =
        ModerationScope(
            permissions = SeerrPermissions(canManageRequests = true, canManageBlocklist = true),
            currentUserId = 7,
            hasBlocklist = true,
        )
    private val bystander = ModerationScope(permissions = SeerrPermissions(), currentUserId = 7, hasBlocklist = false)

    @Test
    fun downFromTheBandLandsOnTheFirstRow() {
        setBoard(manager)
        focusBand()

        pressDown()

        row(HEAT).assertIsFocused()
    }

    @Test
    fun okOnAPillCommitsTheFilterAndOkOnTheChosenOneDoesNot() {
        setBoard(manager)
        focusBand()

        pressOk()
        assertTrue("re-pressing the chosen filter must not re-query", filters.isEmpty())
        pressRight()
        pressOk()

        assertEquals(listOf(RequestFilter.Pending), filters)
    }

    @Test
    fun okOnARowOpensItsSheetOnTheFirstActionAndOkApproves() {
        setBoard(manager)
        focusBand()
        pressDown()

        pressOk()
        sheetRow(R.string.request_approve).assertIsFocused()
        pressOk()

        assertEquals(listOf(1), approved)
        assertEquals(1, dismissed)
    }

    @Test
    fun leftOnTheSheetDismissesIt() {
        setBoard(manager)
        focusBand()
        pressDown()
        pressOk()
        sheetRow(R.string.request_approve).assertIsFocused()

        pressLeft()

        assertEquals(1, dismissed)
        assertTrue(approved.isEmpty())
        settleFocusRestore()
        row(HEAT).assertIsFocused()
    }

    @Test
    fun aDestructiveActionConfirmsFirstAndLandsOnCancel() {
        setBoard(manager)
        focusBand()
        pressDown()
        pressOk()

        pressDown()
        pressDown()
        sheetRow(R.string.request_decline_and_block).assertIsFocused()
        pressOk()
        sheetRow(com.binge.designsystem.R.string.action_cancel).assertIsFocused()
        pressUp()
        pressOk()

        assertEquals(listOf(1 to true), declined)
    }

    @Test
    fun aRowTheViewerCannotActOnStaysInTheWalkButOpensNothing() {
        setBoard(bystander)
        focusBand()
        pressDown()
        row(HEAT).assertIsFocused()

        pressOk()

        assertEquals(0, dismissed)
        composeTestRule.onAllNodes(hasText(string(R.string.request_approve))).assertCountEquals(0)
        pressDown()
        row(BEAR).assertIsFocused()
    }

    private fun setBoard(scope: ModerationScope) {
        val items = listOf(request(1, HEAT), request(2, BEAR))
        composeTestRule.setContent {
            var state by androidx.compose.runtime.remember {
                mutableStateOf(
                    RequestsUiState.Ready(
                        filter = RequestFilter.All,
                        sort = RequestSort.Added,
                        counts = null,
                        scope = scope,
                        actingIds = emptySet(),
                    ),
                )
            }
            BingeTvTheme {
                TvRequestsBoard(
                    state = state,
                    rows = TvPagedRows(count = items.size, at = { items.getOrNull(it) }),
                    events = emptyFlow(),
                    actions =
                        TvRequestsActions(
                            onFilterChange = { filters += it },
                            onOpenActions = { state = state.copy(actionItem = it) },
                            onDismissActions = {
                                dismissed++
                                state = state.copy(actionItem = null)
                            },
                            onApprove = { approved += it },
                            onRetry = {},
                            onDecline = { item, block -> declined += item.id to block },
                            onRemove = { _, _ -> },
                            onRetryLoad = {},
                            onReconnect = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun focusBand() {
        pill(R.string.requests_filter_all).requestFocus()
        composeTestRule.waitForIdle()
        pill(R.string.requests_filter_all).assertIsFocused()
    }

    private fun pill(label: Int) = composeTestRule.onNode(hasTextExactly(string(label)) and isFocusable())

    private fun row(title: String) = composeTestRule.onNode(hasText(title) and isFocusable())

    private fun sheetRow(label: Int) = composeTestRule.onNode(hasText(string(label)) and isFocusable())

    private fun pressDown() = press(Key.DirectionDown)

    private fun pressUp() = press(Key.DirectionUp)

    private fun pressLeft() = press(Key.DirectionLeft)

    private fun pressRight() = press(Key.DirectionRight)

    private fun pressOk() = press(Key.DirectionCenter)

    /** The closer hands focus back one frame after the sheet's nodes go, so the walk resumes from the row that opened it. */
    private fun settleFocusRestore() {
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun request(
        id: Int,
        title: String,
    ) = RequestItem(
        id = id,
        tmdbId = id,
        mediaType = RequestMediaType.Movie,
        title = title,
        posterUrl = null,
        year = "2023",
        requestedBy = "ana",
        requestedById = 3,
        requestedAtMillis = null,
        status = SeerrRequestStatusCode.Pending,
        mediaStatus = null,
        download = null,
        seasonNumbers = emptyList(),
        is4k = false,
    )
}
