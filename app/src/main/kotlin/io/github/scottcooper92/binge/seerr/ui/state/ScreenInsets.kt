package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.PaddingValues

/*
 * The split between the sides a scrolling body keeps outside and the top and bottom it takes inside its
 * scroll is the design system's: `screenOuterPadding`, `screenInnerPadding` and `screenListPadding`. What
 * is left here is the one case only this app has.
 */

/**
 * `screenInnerPadding` for a list with a line pinned above it, such as a refresh bar: the top goes above the
 * line, and this, the rest, goes to the list.
 */
internal fun PaddingValues.belowPinnedLine(): PaddingValues = PaddingValues(bottom = calculateBottomPadding())
