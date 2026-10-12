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
 * One line naming the current choice, opening the shared choice sheet with the full list rather than stacking a
 * chip per option. A list a server supplies has no length or
 * label width this app controls, so it is the only shape whose height does not scale with the
 * connected server's config — see #336.
 *
 * The row is a settings row: [icon] in the tinted box, [title], the current choice as its detail. In the sheet each
 * choice carries [choiceIcon]'s icon, or else the row's, as `choiceSettingItem`'s sheet does.
 *
 * [enabled] is what an editor page passes while it saves: off, it dims
 * the row and blocks the tap that opens the sheet. An already-open sheet closes the moment a save
 * starts too, so a pick made mid-save cannot land in a draft the request has already gone out
 * from — the constraint the chip form carried and this one has to keep by a different route.
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
