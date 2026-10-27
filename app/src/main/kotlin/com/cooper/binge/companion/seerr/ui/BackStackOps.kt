package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Puts [routes] in place of the whole stack, as a link does. Every Back, every screen that leaves on its own and every
 * replaced stack goes through this file, and nothing here can empty it: NavDisplay throws on an empty stack (#1760). The
 * root stays until something replaces it, and Back at the root is the activity's to handle.
 */
internal fun NavBackStack<NavKey>.replaceAll(routes: List<NavKey>) {
    require(routes.isNotEmpty()) { "a back stack needs a root" }
    // One snapshot, written over in place: no reader sees the stack empty, or half of the old one under the new root.
    Snapshot.withMutableSnapshot {
        routes.forEachIndexed { index, route -> if (index < size) set(index, route) else add(route) }
        while (size > routes.size) removeAt(lastIndex)
    }
}

/** Back: takes the top entry off, unless it is the root. False when there was nothing to pop, so the caller can leave. */
internal fun NavBackStack<NavKey>.pop(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}

/**
 * Takes [entry] off when it is still the top, for a screen that leaves on its own: a save, a delete, a disconnect.
 * [entry] is the key instance the entry was built with, compared by `===`: every push of a data-class route is a new
 * instance, so a late callback from an older page equal to the top one leaves the top alone. An object route has a
 * single instance, so [push] never stacks one on itself.
 */
internal fun NavBackStack<NavKey>.popIfTop(entry: NavKey): Boolean {
    if (size <= 1 || lastOrNull() !== entry) return false
    removeAt(lastIndex)
    return true
}

/** Pushes [route], unless an equal route is already the top: a second tap on what is open opens nothing new. */
internal fun NavBackStack<NavKey>.push(route: NavKey) {
    if (lastOrNull() != route) add(route)
}
