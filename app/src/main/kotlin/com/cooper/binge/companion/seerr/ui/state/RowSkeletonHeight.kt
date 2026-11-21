package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * A list row skeleton's height at the font in use: [height] at a font scale of 1, and [growth] more for each whole unit of scale
 * above it. A row's text lines grow with the font and its poster or avatar does not, so a skeleton of one fixed height is too short
 * at a large font and moves every row below it when the list loads.
 */
@Composable
internal fun rowSkeletonHeight(
    height: Dp,
    growth: Dp,
): Dp = height + growth * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
