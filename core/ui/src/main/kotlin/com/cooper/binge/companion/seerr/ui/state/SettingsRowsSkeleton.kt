package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListRowSkeleton
import com.cooper.binge.companion.seerr.core.ui.R
import com.binge.designsystem.R as DesR

private val GROUP_ROW_COUNTS = listOf(2, 3, 2)

/**
 * What a settings page shows while it loads: three plates the height of the groups that arrive, so the page does not jump when the
 * rows land. A spinner says nothing of what is coming; this says it is a few groups of rows.
 */
@Composable
fun SettingsRowsSkeleton(modifier: Modifier = Modifier) {
    val rowHeight = dimensionResource(R.dimen.settings_skeleton_row_height)
    val description = stringResource(R.string.settings_loading)
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        GROUP_ROW_COUNTS.forEachIndexed { index, rows ->
            ListRowSkeleton(height = rowHeight * rows, description = description.takeIf { index == 0 })
        }
    }
}
