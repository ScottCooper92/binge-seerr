package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.cooper.homelab.core.uiandroid.R
import com.binge.designsystem.R as DesR

/**
 * The settings search entry at the top of the Settings tab, built from the design system's search field metrics (its height, start
 * padding, icon and gap) so its icon and text sit exactly where the Search tab's field draws them: switching tabs does not move the
 * field. It opens the settings search when tapped, as the shared entry does.
 */
@Composable
internal fun SettingsSearchField(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = dimensionResource(DesR.dimen.search_bar_height))
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = dimensionResource(DesR.dimen.padding_m), end = dimensionResource(DesR.dimen.search_bar_padding_end)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimensionResource(DesR.dimen.search_bar_icon_size)),
        )
        Spacer(Modifier.width(dimensionResource(DesR.dimen.search_bar_icon_spacing)))
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
