package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/*
 * The split between the sides a scrolling body keeps outside and the top and bottom it takes inside its
 * scroll is the design system's: `screenOuterPadding`, `screenInnerPadding` and `screenListPadding`. What
 * is left here is the two cases only this app has.
 */

/**
 * The sides and the bottom of the window: what a full-bleed page with no top bar and no Scaffold
 * insets — [MediaHeroDetailScaffold]'s — has to clear by hand.
 */
@Composable
internal fun pageEdgeInsets(): WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

/**
 * `screenInnerPadding` for a list with a line pinned above it, such as a refresh bar: the top goes above the
 * line, and this, the rest, goes to the list.
 */
internal fun PaddingValues.belowPinnedLine(): PaddingValues = PaddingValues(bottom = calculateBottomPadding())
