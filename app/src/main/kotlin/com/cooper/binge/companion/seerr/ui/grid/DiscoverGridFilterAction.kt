package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import com.binge.designsystem.BingeIcons
import com.binge.designsystem.theme.contrastSafeOn
import com.binge.designsystem.theme.labelLargeEmphasis
import com.binge.designsystem.theme.labelSmallEmphasis
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The Filters pill in the large title row, as Binge's Discover has it: a grey pill with the compass and "Filters", filled with the
 * accent and a count of what is applied once anything is. It fades out as the bar collapses, and [CollapsedFilterAction] takes over.
 */
@Composable
internal fun FilterActionButton(
    activeCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlighted = activeCount > 0
    val background = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(background)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(
                    horizontal = dimensionResource(R.dimen.grid_filter_action_padding_h),
                    vertical = dimensionResource(DesR.dimen.padding_s),
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.grid_filter_action_spacing)),
    ) {
        Icon(
            imageVector = BingeIcons.Discover,
            contentDescription = stringResource(R.string.grid_open_filters),
            tint = content,
            modifier = Modifier.size(dimensionResource(R.dimen.grid_filter_action_icon)),
        )
        Text(text = stringResource(R.string.grid_filters_button), style = MaterialTheme.typography.labelLargeEmphasis, color = content)
        if (highlighted) CountBadge(activeCount, MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.primary)
    }
}

/** The filter action the bar shows once it has collapsed, fading in as the title row's pill fades out. */
@Composable
internal fun CollapsedFilterAction(
    activeCount: Int,
    enabled: Boolean,
    collapsedFraction: Float,
    onClick: () -> Unit,
) {
    val highlighted = activeCount > 0
    val tint = contrastSafeOn(background = MaterialTheme.colorScheme.background, preferred = MaterialTheme.colorScheme.primary)
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.alpha(collapsedFraction)) {
        BadgedBox(
            badge = { if (highlighted) CountBadge(activeCount, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError) },
        ) {
            Icon(
                imageVector = BingeIcons.Discover,
                contentDescription = stringResource(R.string.grid_open_filters),
                tint = if (highlighted) tint else LocalContentColor.current,
            )
        }
    }
}

/** The applied-filter count in a circle of fixed size, whose number does not scale with the font. */
@Composable
private fun CountBadge(
    count: Int,
    container: Color,
    content: Color,
) {
    Box(
        modifier = Modifier.size(dimensionResource(DesR.dimen.icon_size_l)).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        FixedScale { Text(text = count.toString(), style = MaterialTheme.typography.labelSmallEmphasis, color = content) }
    }
}

/** A number in a fixed circle or badge does not grow with the font: it would clip, and the pill's own label already scales. */
@Composable
private fun FixedScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f), content = content)
}
