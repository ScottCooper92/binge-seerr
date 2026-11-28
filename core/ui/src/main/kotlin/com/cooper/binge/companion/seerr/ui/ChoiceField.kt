package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.core.ui.R

/**
 * One line naming the current choice, taking [onClick] rather than opening a sheet of its own. For a caller that is already a
 * bottom sheet, [EditRequestSheet][com.cooper.binge.companion.seerr.ui.requests.EditRequestSheet], where opening a second one
 * would stack two windows and two scrims (#336), so the caller swaps its own content in [onClick] instead.
 */
@Composable
fun <T> ChoiceField(
    icon: ImageVector,
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (choices.isEmpty()) return
    ItemGroup(
        title = null,
        rows =
            listOf(
                ListItem(
                    icon = icon,
                    label = title,
                    detail = choices.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.settings_value_unknown),
                    enabled = enabled,
                    onClick = onClick,
                ),
            ),
    )
}
