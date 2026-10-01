package io.github.scottcooper92.binge.seerr.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.scottcooper92.binge.seerr.ui.hub.DeveloperRow

/**
 * A release build carries no developer tools at all — the prototype routes and
 * `io.github.scottcooper92.binge.seerr.ui.debug`'s screens exist only in `src/debug` (#467), so
 * the hub gets no rows to open them.
 */
internal fun debugDeveloperRows(backStack: NavBackStack<NavKey>): List<DeveloperRow> = emptyList()

/** No debug routes to register in a release build; see [debugDeveloperRows]. */
internal fun EntryProviderScope<NavKey>.debugDetailEntries(
    backStack: NavBackStack<NavKey>,
    showBack: () -> Boolean,
) = Unit
