package com.cooper.binge.companion.seerr.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

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

/** Puts [route] in place of the top entry when [entry] is still the top, for a screen that reopens itself as another. */
internal fun NavBackStack<NavKey>.replaceTop(
    entry: NavKey,
    route: NavKey,
) {
    if (lastOrNull() === entry) set(lastIndex, route)
}

/** Pushes [route], unless an equal route is already the top: a second tap on what is open opens nothing new. */
internal fun NavBackStack<NavKey>.push(route: NavKey) {
    if (lastOrNull() != route) add(route)
}
