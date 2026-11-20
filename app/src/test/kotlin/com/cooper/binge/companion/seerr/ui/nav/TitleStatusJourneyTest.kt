package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PHONE = "w400dp-h800dp"

private const val WAIT_MILLIS = 5_000L

private const val POPULAR_MOVIES = 5

/**
 * A title's status follows it across screens (#2354), through the real nav host on the demo server: request a film from the
 * page a card opens, go Back, and the card now shows the status the page gave it, with no refresh in between.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class TitleStatusJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private val journey get() = TitleStatusJourney(host)

    private fun awaitStack(vararg stack: NavKey) = host.compose.waitUntil(WAIT_MILLIS) { host.stack() == stack.toList() }

    @Test
    fun `a film requested from its page shows its new status on the Movies hub's card after Back`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        journey.clearFilmRequests()
        host.api { it.updateDiscoverSliders(popularRow()) }
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        scrollHubTo(FILM)
        host.await("$FILM on the hub without a status") { host.isShowing(FILM) && !journey.cardReads("") }

        journey.requestFromTheCardAndGoBack()

        awaitStack(ShellRoute)
        assertTrue("the request for $FILM reached the server", journey.serverRequests().isNotEmpty())
        journey.awaitCard("hub")
    }

    @Test
    fun `a film requested from its page shows its new status on the Discover grid's card after Back`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        journey.clearFilmRequests()
        val grid = DiscoverGridRoute(TitleKind.Movie, GridPreset.Popular, host.string(R.string.hubs_row_popular_movies))
        host.launch(HomeRoute, grid)
        host.awaitShowing(FILM)
        host.await("$FILM on the grid without a status") { !journey.cardReads("") }

        journey.requestFromTheCardAndGoBack()

        awaitStack(ShellRoute, grid)
        assertTrue("the request for $FILM reached the server", journey.serverRequests().isNotEmpty())
        journey.awaitCard("grid")
    }

    /** The demo seeds no Popular row, so the server is given the built-in one. */
    private fun popularRow() =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("id", 1)
                    put("type", POPULAR_MOVIES)
                    put("isBuiltIn", true)
                    put("enabled", true)
                },
            )
        }

    /** Rows load one by one and the list composes only what is near, so this scrolls again until the row it wants has arrived. */
    private fun scrollHubTo(text: String) {
        host.compose.waitUntil(WAIT_MILLIS) {
            try {
                host.compose
                    .onAllNodes(hasScrollAction())
                    .onFirst()
                    .performScrollToNode(hasText(text, substring = true))
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }
}
