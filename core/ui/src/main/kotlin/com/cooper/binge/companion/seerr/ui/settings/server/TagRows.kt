package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.CheckboxRow
import com.cooper.binge.companion.seerr.core.ui.R

@Composable
fun TagRows(
    tags: List<com.cooper.binge.companion.seerr.ui.Choice>,
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    if (tags.isEmpty()) return
    Text(stringResource(R.string.request_tags), style = MaterialTheme.typography.titleSmall)
    tags.forEach { tag ->
        CheckboxRow(
            label = tag.label,
            checked = tag.id in selected,
            onCheckedChange = { onToggle(tag.id) },
            enabled = enabled,
            icon = Icons.AutoMirrored.Filled.Label,
        )
    }
}
