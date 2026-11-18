package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.ui.CollectionRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.TitleCreditsRoute
import com.cooper.binge.companion.seerr.ui.TitleDetailRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

private const val PHONE = "w400dp-h800dp"

/** The demo's first film, and its name on the page. */
private const val DEMO_FILM_ID = 10378
private const val DEMO_FILM_NAME = "Big Buck Bunny"

/** The demo's collection, and its name on the page. */
private const val DEMO_COLLECTION_ID = 9500
private const val DEMO_COLLECTION_NAME = "The Demo Collection"

/** The collection page's count, which the film page does not show. */
private const val DEMO_COLLECTION_FILMS = "3 films"

/**
 * The title pages through the real nav host, on the demo server: each route the title entries register still resolves to their
 * screens, and Back leaves each one.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class TitleEntriesJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private fun awaitStack(vararg stack: NavKey) = host.compose.waitUntil(WAIT_MILLIS) { host.stack() == stack.toList() }

    @Test
    fun `a film's page opens and Back leaves it`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        val route = TitleDetailRoute(TitleKind.Movie, DEMO_FILM_ID)
        host.launch(HomeRoute, route)

        host.awaitShowing(DEMO_FILM_NAME)
        host.back()
        awaitStack(ShellRoute)
    }

    @Test
    fun `See all on a film's collection rail opens the collection and Back returns to the film`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        val film = TitleDetailRoute(TitleKind.Movie, DEMO_FILM_ID)
        host.launch(HomeRoute, film)
        host.awaitShowing(DEMO_FILM_NAME)
        val seeAll = hasContentDescription(host.string(DesR.string.action_see_all_in_section, DEMO_COLLECTION_NAME))
        host.compose.waitUntil(WAIT_MILLIS) {
            host.compose
                .onAllNodes(seeAll)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        host.compose
            .onNode(seeAll)
            .performScrollTo()
            .performClick()
        host.awaitShowing(DEMO_COLLECTION_FILMS)

        assertEquals(listOf<NavKey>(ShellRoute, film, CollectionRoute(DEMO_COLLECTION_ID)), host.stack())
        host.back()
        host.awaitShowing(DEMO_FILM_NAME)
        assertEquals(listOf<NavKey>(ShellRoute, film), host.stack())
    }

    @Test
    fun `a collection's page opens and Back leaves it`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, CollectionRoute(DEMO_COLLECTION_ID))

        host.awaitShowing(DEMO_COLLECTION_NAME)
        host.back()
        awaitStack(ShellRoute)
    }

    @Test
    fun `the credits page opens above a film's page and Back returns to it`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        val film = TitleDetailRoute(TitleKind.Movie, DEMO_FILM_ID)
        val credits = TitleCreditsRoute(TitleKind.Movie, DEMO_FILM_ID)
        host.launch(HomeRoute, film, credits)

        host.awaitShowing(host.string(R.string.title_credits_title))
        host.back()
        host.awaitShowing(DEMO_FILM_NAME)
        assertEquals(listOf<NavKey>(ShellRoute, film), host.stack())
    }
}

private const val WAIT_MILLIS = 5_000L
