package com.cooper.binge.companion.seerr.ui

import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.cooper.binge.companion.seerr.util.entryMetadata
import com.cooper.binge.companion.seerr.util.routesWithoutEntries
import com.cooper.binge.companion.seerr.util.sampleRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ShellPanesStrategy] draws what sits above the shell from the [SectionPane] marker on the entry, while the stash and
 * [coversWindow] decide from [opensBesideHub], which sees only the key. They must name the same routes: a route drawn in the Settings
 * tab but left on the app's stack when the user leaves it swallows whatever another tab opens next (#2367, #2380). Every route is
 * checked, so a new one given the marker without the predicate, or the reverse, fails here.
 */
class SectionMarkerAgreementTest {
    private fun carriesSectionMarker(route: NavKey): Boolean {
        val metadata = entryMetadata(route)
        return SectionPane.all { (key, value) -> metadata[key] == value }
    }

    @Test
    fun `a route carries the section marker exactly when opensBesideHub says it opens beside the hub`() {
        val disagree =
            sampleRoutes()
                .filter { it::class !in routesWithoutEntries }
                .filter { carriesSectionMarker(it) != it.opensBesideHub() }
                .map { "${it::class.simpleName} (marker=${carriesSectionMarker(it)}, opensBesideHub=${it.opensBesideHub()})" }

        assertEquals("the marker and opensBesideHub() disagree on: $disagree", emptyList<String>(), disagree)
    }

    @Test
    fun `the settings search and every hub section open beside the hub and nothing else does`() {
        val beside =
            sampleRoutes()
                .filter { it::class !in routesWithoutEntries }
                .filter { it.opensBesideHub() }
                .map { it::class }
                .toSet()

        val expected = HubSection.entries.map { it.route()::class }.toSet() + SettingsSearchRoute::class
        assertEquals(expected, beside)
    }
}
