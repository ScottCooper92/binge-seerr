package io.github.scottcooper92.binge.seerr.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.PaneContent
import io.github.scottcooper92.binge.seerr.ui.debug.DeveloperOptionsScreen
import io.github.scottcooper92.binge.seerr.ui.debug.ManageSheetPrototypeScreen

/** The hub's entry point into developer tools, present only in a debug build. */
internal fun debugOpenDeveloperOptions(backStack: NavBackStack<NavKey>): (() -> Unit)? = { backStack.add(DeveloperOptionsRoute) }

/**
 * Developer options opens straight off the hub, so it takes [showBack] like the account card's
 * destination; the prototype is always stacked above it, so its arrow always shows.
 */
internal fun EntryProviderScope<NavKey>.debugDetailEntries(
    backStack: NavBackStack<NavKey>,
    showBack: () -> Boolean,
) {
    entry<DeveloperOptionsRoute>(metadata = DetailPane) {
        PaneContent {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            DeveloperOptionsScreen(
                onBack = back.takeIf { showBack() },
                onOpenManageSheetPrototype = { backStack.add(ManageSheetPrototypeRoute) },
            )
        }
    }
    entry<ManageSheetPrototypeRoute>(metadata = DetailPane) {
        PaneContent { ManageSheetPrototypeScreen(onBack = { backStack.removeLastOrNull() }) }
    }
}
