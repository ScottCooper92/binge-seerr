package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.SortSheet

/**
 * One line naming the current choice, opening the shared choice sheet with the full list rather than a
 * chip per option. A server-supplied list has no length or label width this app controls, so this is the
 * only shape whose height does not scale with the server's config (#336).
 *
 * A settings row: [icon] in the tinted box, [title], the current choice as its detail. In the sheet each
 * choice carries [choiceIcon]'s icon, or else the row's, as `choiceSettingItem`'s sheet does.
 *
 * @param enabled off while an editor page saves: it dims the row and blocks the tap. An open sheet closes
 *   when a save starts too, so a mid-save pick cannot land in a draft the request has already gone out from.
 */
@Composable
fun <T> ChoiceRow(
    icon: ImageVector,
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    choiceIcon: (T) -> ImageVector? = { null },
) {
    if (choices.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    ChoiceField(icon = icon, title = title, choices = choices, selected = selected, enabled = enabled) { open = true }
    if (open) {
        SortSheet(
            title = title,
            choices = choices.map { it.first },
            selected = selected,
            label = { id -> choices.first { it.first == id }.second },
            icon = { choiceIcon(it) ?: icon },
            onSelect = onSelect,
            onDismiss = { open = false },
        )
    }
}

/**
 * [ChoiceRow]'s row alone, taking [onClick] rather than owning a sheet. For a caller that is
 * already a bottom sheet — [EditRequestSheet][com.cooper.binge.companion.seerr.ui.requests.EditRequestSheet],
 * per #336's "sheets inside sheets" — where opening a second one would stack two windows and two
 * scrims, so the caller swaps its own content in [onClick] instead.
 */
@Composable
internal fun <T> ChoiceField(
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
