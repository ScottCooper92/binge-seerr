package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.IconButtonTone
import com.binge.designsystem.theme.BingeTheme
import com.cooper.binge.companion.seerr.feature.discover.R
import com.binge.designsystem.R as DesR

/** Share in the page's bar, beside back. [glassBackgroundAlpha] is the bar's own fade: the glass hands over to the bar as it fills. */
@Composable
fun ShareBarAction(
    glassBackgroundAlpha: Float,
    onShare: () -> Unit,
) {
    ExpressiveIconButton(
        onClick = onShare,
        icon = Icons.Filled.Share,
        contentDescription = stringResource(R.string.title_page_action_share),
        tint = lerp(BingeTheme.colors.onScrim, MaterialTheme.colorScheme.onBackground, 1f - glassBackgroundAlpha),
        tone = IconButtonTone.Glass,
        size = dimensionResource(DesR.dimen.top_bar_icon_size),
        glassBackgroundAlpha = glassBackgroundAlpha,
    )
}
