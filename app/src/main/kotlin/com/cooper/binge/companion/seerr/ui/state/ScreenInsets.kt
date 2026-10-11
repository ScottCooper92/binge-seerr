package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import com.binge.designsystem.resolvedContentPadding

/*
 * The split between the sides a scrolling body keeps outside and the top and bottom it takes inside its
 * scroll is the design system's: `screenOuterPadding`, `screenInnerPadding` and `screenListPadding`. What
 * is left here are the two cases only this app has: `belowPinnedLine` and `filteredPagePadding`.
 */

/**
 * `screenInnerPadding` for a list with a line pinned above it, such as a refresh bar: the top goes above the
 * line, and this, the rest, goes to the list.
 */
internal fun PaddingValues.belowPinnedLine(): PaddingValues = PaddingValues(bottom = calculateBottomPadding())

/**
 * The page's top and bottom, which already hold the bar, the header and the content inset below, with the content inset
 * on the sides. The template hands a page only top and bottom, so the sides are this list's own.
 */
@Composable
internal fun PaddingValues.filteredPagePadding(): PaddingValues {
    val sides = resolvedContentPadding()
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = sides.calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = sides.calculateEndPadding(direction),
        bottom = calculateBottomPadding(),
    )
}
