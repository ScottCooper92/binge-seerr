package io.github.scottcooper92.binge.seerr.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * A release build carries no developer tools at all — `DeveloperOptionsRoute` and
 * `io.github.scottcooper92.binge.seerr.ui.debug`'s screens exist only in `src/debug` (#467), so
 * the hub gets no entry point to open them.
 */
internal fun debugOpenDeveloperOptions(backStack: NavBackStack<NavKey>): (() -> Unit)? = null

/** No debug routes to register in a release build; see [debugOpenDeveloperOptions]. */
internal fun EntryProviderScope<NavKey>.debugDetailEntries(
    backStack: NavBackStack<NavKey>,
    showBack: () -> Boolean,
) = Unit
