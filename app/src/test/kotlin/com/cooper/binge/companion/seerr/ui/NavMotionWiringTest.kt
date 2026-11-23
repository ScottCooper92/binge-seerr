package com.cooper.binge.companion.seerr.ui

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.cooper.binge.companion.seerr.util.entryMetadata
import com.cooper.binge.companion.seerr.util.routesWithoutEntries
import com.cooper.binge.companion.seerr.util.sampleRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/**
 * Which shared navigation motion each route's entry carries (#2264): the detail set on the pages that take the whole window, the tab
 * predictive pop alone on what the Settings tab and its panes drill into, and none on the roots. Every route is classified here, so one
 * added later without a set fails this test rather than animating by Navigation 3's defaults unnoticed.
 */
class NavMotionWiringTest {
    private val specs = navMotionSpecs { error("a transition was run") }
    private val forward = NavDisplay.transitionSpec { error("unused") }.keys.single()
    private val pop = NavDisplay.popTransitionSpec { error("unused") }.keys.single()
    private val predictive = NavDisplay.predictivePopTransitionSpec { _ -> error("unused") }.keys.single()

    private val detail: Set<KClass<*>> =
        setOf(
            TitleDetailRoute::class,
            CollectionRoute::class,
            PersonDetailRoute::class,
            SeriesEpisodesRoute::class,
            TitleCreditsRoute::class,
            DiscoverGridRoute::class,
            SettingsSearchRoute::class,
            EditConnectionRoute::class,
        )

    private val tab: Set<KClass<*>> =
        setOf(
            RequestsRoute::class,
            IssuesRoute::class,
            BlocklistRoute::class,
            UsersRoute::class,
            SettingsRoute::class,
            ThisAppRoute::class,
            RequestDetailRoute::class,
            IssueDetailRoute::class,
            BlocklistDetailRoute::class,
            UserDetailRoute::class,
            UserSettingsRoute::class,
            UserSettingsPageRoute::class,
            ServerSettingsPageRoute::class,
            LicencesRoute::class,
            DiagnosticsRoute::class,
            DiscoverSliderRoute::class,
            NotificationAgentRoute::class,
            DvrInstanceRoute::class,
            OverrideRuleRoute::class,
        )

    /** The developer prototypes of a debug build: drill-downs from Settings, wired with the tab set where they exist. */
    private companion object {
        val DEBUG_ONLY = setOf("ManageSheetPrototypeRoute", "BingeHintPrototypeRoute", "FlagOverridesRoute", "RequestCardsPrototypeRoute")
    }

    /** The roots and the shell: leaving the app is the system's back, so they animate as Navigation 3 does. */
    private val roots: Set<KClass<*>> = routesWithoutEntries

    private fun metadataOf(route: NavKey): Map<String, Any> = entryMetadata(route, specs)

    @Test
    fun `a page that takes the whole window carries the detail set`() {
        sampleRoutes().filter { it::class in detail }.forEach { route ->
            val keys = metadataOf(route).keys
            assertTrue("${route::class.simpleName} lacks the forward transition", forward in keys)
            assertTrue("${route::class.simpleName} lacks the pop transition", pop in keys)
            assertTrue("${route::class.simpleName} lacks the predictive pop", predictive in keys)
        }
    }

    @Test
    fun `a drill-down in the Settings tab carries the tab predictive pop alone`() {
        sampleRoutes().filter { it::class in tab }.forEach { route ->
            val keys = metadataOf(route).keys
            assertTrue("${route::class.simpleName} lacks the predictive pop", predictive in keys)
            assertFalse("${route::class.simpleName} overrides the forward transition", forward in keys)
            assertFalse("${route::class.simpleName} overrides the pop transition", pop in keys)
        }
    }

    @Test
    fun `every route is classified, so a new one cannot animate by default unnoticed`() {
        val classified = detail + tab + roots
        val unclassified = sampleRoutes().map { it::class }.filter { it !in classified && it.simpleName !in DEBUG_ONLY }
        assertEquals("classify these routes in NavMotionWiringTest: $unclassified", emptyList<KClass<*>>(), unclassified)
    }
}
