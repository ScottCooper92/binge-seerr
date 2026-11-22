package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.component.ListItem

/**
 * One result of a search that collects things, as a row of a group: what it is, and whether it has been added. The whole
 * row toggles it, announced as a tick row, and a check at the end says it is in. [supporting] is an optional second line.
 */
fun searchResultItem(
    title: String,
    added: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    supporting: String? = null,
): ListItem =
    ListItem(
        icon = icon,
        label = title,
        detail = supporting,
        checked = added,
        trailingContent = { if (added) Icon(Icons.Filled.Check, contentDescription = null) },
        onClick = onToggle,
    )
