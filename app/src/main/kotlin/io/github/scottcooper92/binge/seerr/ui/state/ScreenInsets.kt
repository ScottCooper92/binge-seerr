package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

/*
 * The window is drawn edge to edge and the top bar is transparent, so a Scaffold hands its body the
 * top bar's height and the system bars' insets as padding. A body that scrolls splits that padding in
 * two. The outer part, the sides, stays outside the body. The inner part, the top and the bottom,
 * goes inside its scroll: rows start below the bar and scroll up under it, and scroll down behind the
 * navigation bar with the last one still coming to rest above it. A body that does not scroll (a
 * loading, empty or error state) pads by the inner part, so it sits in the same space.
 */

/** The part of a Scaffold's padding that stays outside a scrolling body: the sides. */
@Composable
internal fun PaddingValues.outerPadding(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(start = calculateStartPadding(direction), end = calculateEndPadding(direction))
}

/** The part of a Scaffold's padding that goes inside a scrolling body: the top bar above it and the navigation bar below. */
internal fun PaddingValues.innerPadding(): PaddingValues = PaddingValues(top = calculateTopPadding(), bottom = calculateBottomPadding())

/**
 * [innerPadding] for a list with a line pinned above it, such as a refresh bar: the top goes above the line,
 * and this, the rest, goes to the list.
 */
internal fun PaddingValues.belowPinnedLine(): PaddingValues = PaddingValues(bottom = calculateBottomPadding())
