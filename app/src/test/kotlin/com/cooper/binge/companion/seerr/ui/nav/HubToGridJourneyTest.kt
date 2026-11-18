package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PHONE = "w400dp-h800dp"

private const val WAIT_MILLIS = 5_000L

private const val POPULAR_MOVIES = 5
private const val MOVIE_GENRES = 6
private const val STUDIOS = 8

/**
 * The Movies hub through the real nav host, on the demo server: a genre tile and a row's "See all" each open the grid with the
 * preset they stand for, the Recent requests row's "See all" opens the request browser, and Back returns to the hub.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class HubToGridJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private fun openMoviesHub(seedSliders: Boolean = true) {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        if (seedSliders) host.api { it.updateDiscoverSliders(movieSliders()) }
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_requests))
        host.compose.onNodeWithTag("tab_movies").performClick()
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
    }

    /** The demo seeds five sliders and none of them is a tile row, so the server is given the built-ins this journey needs. */
    private fun movieSliders() =
        buildJsonArray {
            listOf(POPULAR_MOVIES, MOVIE_GENRES, STUDIOS).forEachIndexed { index, type ->
                add(
                    buildJsonObject {
                        put("id", index + 1)
                        put("type", type)
                        put("isBuiltIn", true)
                        put("enabled", true)
                    },
                )
            }
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

    private fun awaitStack(vararg stack: NavKey) = host.compose.waitUntil(WAIT_MILLIS) { host.stack() == stack.toList() }

    @Test
    fun `see all on the recent requests row opens the request browser and Back returns to the hub`() {
        openMoviesHub(seedSliders = false)
        scrollHubTo(host.string(R.string.hubs_row_recent_requests))
        host.compose.onNodeWithContentDescription("See all in ${host.string(R.string.hubs_row_recent_requests)}").performClick()

        awaitStack(ShellRoute, RequestsRoute)
        host.back()
        awaitStack(ShellRoute)
        host.awaitShowing(host.string(R.string.hubs_row_recent_requests))
    }

    @Test
    fun `browse all opens the grid on the popular list and Back returns to the hub`() {
        openMoviesHub()
        host.compose.onNodeWithText(host.string(R.string.hubs_browse_all_movies)).performClick()

        awaitStack(ShellRoute, DiscoverGridRoute(TitleKind.Movie, GridPreset.Popular, host.string(R.string.hubs_row_popular_movies)))
        host.back()
        awaitStack(ShellRoute)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
    }

    @Test
    fun `a genre tile opens the grid on that genre and Back returns to the hub`() {
        openMoviesHub()
        scrollHubTo(host.string(R.string.hubs_row_genres))
        host.compose.onNodeWithText("Action", useUnmergedTree = true).performClick()

        awaitStack(ShellRoute, DiscoverGridRoute(TitleKind.Movie, GridPreset.Genre(28), "Action"))
        host.back()
        awaitStack(ShellRoute)
        host.awaitShowing(host.string(R.string.hubs_row_genres))
    }

    @Test
    fun `a see all opens the grid on the row's list and Back returns to the hub`() {
        openMoviesHub()
        scrollHubTo(host.string(R.string.hubs_row_popular_movies))
        host.compose.onNodeWithContentDescription("See all in ${host.string(R.string.hubs_row_popular_movies)}").performClick()

        awaitStack(ShellRoute, DiscoverGridRoute(TitleKind.Movie, GridPreset.Popular, host.string(R.string.hubs_row_popular_movies)))
        host.back()
        awaitStack(ShellRoute)
        host.awaitShowing(host.string(R.string.hubs_row_popular_movies))
    }
}
