package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import com.binge.designsystem.template.PagedPhase
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PAGE = "page"
private const val ROWS = 30

private val idle = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(false), LoadState.NotLoading(false))
private val loading = idle.copy(refresh = LoadState.Loading)

private fun states(
    source: LoadStates = idle,
    mediator: LoadStates? = idle,
) = CombinedLoadStates(
    refresh = mediator?.refresh ?: source.refresh,
    prepend = source.prepend,
    append = source.append,
    source = source,
    mediator = mediator,
)

/**
 * The paged lists' pull. The refresh it calls is `LazyPagingItems.refresh()`, which lives in the UI rather than a
 * view model, so this drives [PagedPullToRefresh] with a counted refresh: a pull refreshes once, and a pull while a
 * refresh is in flight, behind rows or under a skeleton, starts none.
 */
@RunWith(RobolectricTestRunner::class)
class PagedPullToRefreshTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var refreshes = 0

    private fun show(
        phase: PagedPhase,
        loadState: CombinedLoadStates,
    ) = rule.setContent {
        SeerrTheme {
            PagedPullToRefresh(phase = phase, loadState = loadState, onRefresh = { refreshes++ }, contentPadding = PaddingValues()) {
                if (phase == PagedPhase.Empty) {
                    PullableMessage(Modifier.testTag(PAGE)) { fill -> EmptyScreen(message = "Nothing here", modifier = fill) }
                } else {
                    LazyColumn(Modifier.fillMaxSize().testTag(PAGE)) { items(ROWS) { Text("Row $it") } }
                }
            }
        }
    }

    private fun pull() {
        rule.onNodeWithTag(PAGE).performTouchInput { swipeDown(startY = top, endY = bottom) }
        rule.waitForIdle()
    }

    @Test
    fun `a pull on rows refreshes once`() {
        show(PagedPhase.Rows(refreshing = false, refreshError = null), states())

        pull()

        assertEquals(1, refreshes)
    }

    @Test
    fun `a pull while the rows refresh starts no second`() {
        show(PagedPhase.Rows(refreshing = true, refreshError = null), states(mediator = loading))

        pull()

        assertEquals(0, refreshes)
    }

    @Test
    fun `a pull under the first load's skeleton starts no second`() {
        show(PagedPhase.Skeleton, states(source = loading, mediator = null))

        pull()

        assertEquals(0, refreshes)
    }

    @Test
    fun `an empty list can be pulled`() {
        show(PagedPhase.Empty, states())

        pull()

        assertEquals(1, refreshes)
    }

    @Test
    fun `a refresh is in flight while either side of the list is loading it`() {
        assertFalse(states().refreshInFlight())
        assertTrue(states(mediator = loading).refreshInFlight())
        assertTrue(states(source = loading).refreshInFlight())
        assertTrue(states(source = loading, mediator = null).refreshInFlight())
        assertFalse(states(mediator = null).refreshInFlight())
    }
}
