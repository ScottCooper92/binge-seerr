package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.LocalTopBarActionTint
import com.binge.designsystem.component.LocalTopBarActionTone
import com.binge.designsystem.R as DesR

/**
 * An icon action in a screen's top bar, in the round button the bar draws beside Back. [glassBackgroundAlpha] is the bar's own
 * fade, the value the scaffold hands its `actions` slot.
 */
@Composable
internal fun BarIconAction(
    icon: ImageVector,
    contentDescription: String,
    glassBackgroundAlpha: Float,
    onClick: () -> Unit,
) {
    ExpressiveIconButton(
        onClick = onClick,
        icon = icon,
        contentDescription = contentDescription,
        tint = LocalTopBarActionTint.current,
        tone = LocalTopBarActionTone.current,
        size = dimensionResource(DesR.dimen.top_bar_icon_size),
        glassBackgroundAlpha = glassBackgroundAlpha,
    )
}
