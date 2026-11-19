package com.cooper.binge.companion.seerr.util

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.NavMotionSpecs
import com.cooper.binge.companion.seerr.ui.PaneLayout
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.discover.title.titleEntries
import com.cooper.binge.companion.seerr.ui.grid.discoverGridEntries
import com.cooper.binge.companion.seerr.ui.navMotionSpecs
import com.cooper.binge.companion.seerr.ui.settingsEntries
import kotlin.reflect.KClass

/**
 * The routes the host builds itself (`SeerrNavHost`'s `homeEntries`), so [entryMetadata] has no entry for them: the roots and the
 * shell, which never sit above the shell.
 */
internal val routesWithoutEntries: Set<KClass<*>> = setOf(HomeRoute::class, ShellRoute::class, HubRoute::class)

/** Specs whose transitions fail the test if one is ever run: a test reading an entry's metadata never plays one. */
internal fun inertNavMotionSpecs(): NavMotionSpecs = navMotionSpecs { error("a transition was run") }

/**
 * The metadata of [route]'s entry in the app's real entry provider, built from the same entry builders `SeerrNavHost` registers
 * (`settingsEntries`, `discoverGridEntries`, `titleEntries`). When a builder is added there, add it here once and every test that
 * reads real entries sees it; a route none of them builds fails with the provider's own missing-entry error.
 */
internal fun entryMetadata(
    route: NavKey,
    specs: NavMotionSpecs = inertNavMotionSpecs(),
): Map<String, Any> {
    val stack = NavBackStack<NavKey>(HomeRoute)
    val panes = PaneLayout(hubBeside = { false }, showBack = { true }, motion = specs)
    val provider =
        entryProvider<NavKey> {
            titleEntries(stack, specs)
            discoverGridEntries(stack, specs, onOpenAccount = {})
            settingsEntries(stack, connected = { true }, panes = panes, onExit = {})
        }
    return provider(route).metadata
}
