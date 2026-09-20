package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.binge.designsystem.modifier.skeleton
import com.binge.designsystem.theme.BingeShapes

/**
 * The plate every hand-built detail-page skeleton in this app is built from — one shimmering,
 * rounded rectangle, sized by its caller. Shared rather than duplicated per skeleton (`RequestDetailSkeleton`,
 * `UserDetailSkeleton`, …) since it is the one primitive every one of them repeats.
 */
@Composable
internal fun SkeletonPlate(
    modifier: Modifier = Modifier,
    shape: Shape = BingeShapes.ElementSmall,
) {
    Box(modifier.skeleton(visible = true, shape = shape))
}
