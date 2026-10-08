package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.TextEntrySurface
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.PeekingListSheet
import com.binge.designsystem.R as DesR

/**
 * A text setting as a list row for an `ItemGroup`: its name, and its value (or [emptyLabel] when blank) as the detail;
 * [shown] replaces that for a value the row shouldn't spell out, such as a key.
 * A tap edits it in the design system's text entry sheet; [check] names what is wrong with a value, which keeps Done
 * off. The sheet belongs to this call, so a page lists its rows and nothing else.
 */
@Composable
internal fun textSettingItem(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    emptyLabel: String = stringResource(R.string.settings_value_not_set),
    hint: String? = null,
    check: (String) -> String? = { null },
    shown: String = value.ifBlank { emptyLabel },
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(value) }
        val problem = check(draft.trim())
        BingeBottomSheet(onDismissRequest = { open = false }) {
            TextEntrySurface(
                title = label,
                value = draft,
                onValueChange = { draft = it },
                onSubmit = {
                    onChange(draft.trim())
                    open = false
                },
                onCancel = { open = false },
                submitLabel = stringResource(R.string.editor_done),
                hint = hint,
                submitEnabled = problem == null,
                error = problem.takeIf { draft.isNotEmpty() },
                minLines = 1,
                maxLines = 1,
                modifier = Modifier.imePadding(),
            )
        }
    }
    return ListItem(
        icon = icon,
        label = label,
        detail = shown,
        clickable = enabled,
        disabled = !enabled,
        onClick = { open = true },
    )
}

/**
 * A pick from a fixed list as a list row: the setting's name, and what it is set to now. A tap opens a peeking sheet
 * of the choices as list rows, the current one checked; picking one applies it and closes. The sheet closes if a save
 * starts, so a pick can't land in a draft that has already gone out.
 */
@Composable
internal fun <T> choiceSettingItem(
    icon: ImageVector,
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    enabled: Boolean,
    onSelect: (T) -> Unit,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open) {
        PeekingListSheet(title = title, onDismiss = { open = false }) {
            ChoiceRows(icon, choices, selected) { choice ->
                onSelect(choice)
                open = false
            }
        }
    }
    return ListItem(
        icon = icon,
        label = title,
        detail = choices.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.settings_value_unknown),
        clickable = enabled,
        disabled = !enabled,
        onClick = { open = true },
    )
}

/** [choices] as list rows in a sheet, the [selected] one checked, for a picker built on [PeekingListSheet]. */
@Composable
internal fun <T> ChoiceRows(
    icon: ImageVector,
    choices: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    ItemGroup(
        title = null,
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        rows =
            choices.map { (choice, label) ->
                ListItem(
                    icon = icon,
                    label = label,
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    trailingContent =
                        if (choice == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            {}
                        },
                )
            },
    )
    Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_l)))
}
