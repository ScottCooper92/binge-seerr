package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

/*
 * The window is drawn edge to edge, so a Scaffold hands its body the system bars' insets as padding.
 * A body that scrolls splits that padding in two. The outer part stays outside the body. The inner
 * part goes inside its scroll, so rows scroll behind the navigation bar and the last one still comes
 * to rest above it. A body that does not scroll (a loading, empty or error state) pads by the inner
 * part, so it is centred in the same space.
 */

/** The part of a Scaffold's padding that stays outside a scrolling body: the top and the sides. */
@Composable
internal fun PaddingValues.outerPadding(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
    )
}

/** The part of a Scaffold's padding that goes inside a scrolling body: the bottom, over the navigation bar. */
internal fun PaddingValues.innerPadding(): PaddingValues = PaddingValues(bottom = calculateBottomPadding())
