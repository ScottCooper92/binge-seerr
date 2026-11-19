package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PHONE = "w400dp-h800dp"

private const val WAIT_MILLIS = 5_000L

private const val POPULAR_MOVIES = 5

/** A demo film nobody has requested, third in the demo's catalog so it is near the start of every list. */
private const val FILM = "Tears of Steel"

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

    private val request get() = host.string(R.string.title_page_request)
    private val requested get() = host.string(R.string.hubs_card_requested)

    private fun awaitStack(vararg stack: NavKey) = host.compose.waitUntil(WAIT_MILLIS) { host.stack() == stack.toList() }

    private fun card(): SemanticsNodeInteraction = host.compose.onAllNodes(hasText(FILM, substring = true)).onFirst()

    /** The card for [FILM] reads [chip] on it now. A card is one merged node, so its title and its chip are in the same text. */
    private fun cardReads(chip: String): Boolean =
        host.compose
            .onAllNodes(hasText(FILM, substring = true) and hasText(chip, substring = true))
            .fetchSemanticsNodes()
            .isNotEmpty()

    /**
     * The "Request" that is lowest on screen. The page's own button and the sheet's share a label, and a click that reaches the
     * page's button through the sheet's scrim only dismisses the sheet, so the one pinned in the sheet's footer is the one wanted.
     */
    private fun lowestRequest(): SemanticsNodeInteraction {
        val nodes = host.compose.onAllNodes(hasText(request))
        val lowest = nodes.fetchSemanticsNodes().withIndex().maxByOrNull { it.value.boundsInRoot.top }?.index ?: 0
        return nodes[lowest]
    }

    /** Opens [FILM]'s page from its card, requests it from the sheet, and comes back with Back. */
    private fun requestFromTheCardAndGoBack() {
        host.clickUntil("the page of $FILM", target = { card().performScrollTo() }, reached = { host.isShowing(request, exactly = true) })
        host.clickUntil(
            "the request sheet",
            target = { host.compose.onAllNodes(hasText(request)).onFirst() },
            reached = { host.isShowing(host.string(R.string.request_sheet_status_new)) },
        )
        host.clickUntil(
            "$FILM requested",
            target = { lowestRequest() },
            reached = { host.isShowing(requested, exactly = true) },
        )
        host.back()
    }

    @Test
    fun `a film requested from its page shows as requested on the Movies hub's card after Back`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.api { it.updateDiscoverSliders(popularRow()) }
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        scrollHubTo(FILM)
        host.await("$FILM on the hub without a status") { host.isShowing(FILM) && !cardReads(requested) }

        requestFromTheCardAndGoBack()

        awaitStack(ShellRoute)
        host.await("the hub's card for $FILM reads $requested") { cardReads(requested) }
    }

    @Test
    fun `a film requested from its page shows as requested on the Discover grid's card after Back`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        val grid = DiscoverGridRoute(TitleKind.Movie, GridPreset.Popular, host.string(R.string.hubs_row_popular_movies))
        host.launch(HomeRoute, grid)
        host.awaitShowing(FILM)
        host.await("$FILM on the grid without a status") { !cardReads(requested) }

        requestFromTheCardAndGoBack()

        awaitStack(ShellRoute, grid)
        host.await("the grid's card for $FILM reads $requested") { cardReads(requested) }
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
