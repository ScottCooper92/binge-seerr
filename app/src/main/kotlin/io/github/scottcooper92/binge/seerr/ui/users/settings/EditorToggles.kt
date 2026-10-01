package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Constraints
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.component.SettingsRows
import com.binge.designsystem.R as DesR

/**
 * A toggle drawn as the design system's switch [SettingsRow], the same as the Notify me group's.
 * [dimSwitch] is for a row inside a group, where nothing else dims it; a lone [EditorToggleRow] dims whole.
 */
internal fun editorToggle(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    detail: String? = null,
    dimSwitch: Boolean = false,
    onToggle: (Boolean) -> Unit,
) = SettingsRow(
    icon = icon,
    label = label,
    detail = detail,
    clickable = enabled,
    trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled || !dimSwitch) },
    onClick = { onToggle(!checked) },
)

/** A card that holds only toggles: a plain `SettingsGroup`, dividers and all. */
@Composable
internal fun EditorToggleGroup(
    title: String,
    toggles: List<SettingsRow>,
    modifier: Modifier = Modifier,
) = SettingsGroup(
    title = title,
    rows = toggles,
    modifier = modifier.alpha(if (toggles.all { !it.clickable }) DISABLED_CONTENT_ALPHA else 1f),
)

/**
 * One toggle inside an [EditorSectionCard] beside text fields. It bleeds past the card's padding so
 * the row's own inset lines its icon up with the card edge, as it does in a [SettingsGroup].
 */
@Composable
internal fun EditorToggleRow(toggle: SettingsRow) {
    // The design-system row dims only its switch; dim the icon and label with it so a row that is off reads as off.
    val enabled = toggle.clickable
    val bleed = dimensionResource(DesR.dimen.padding_m)
    SettingsRows(
        rows = listOf(toggle),
        modifier =
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA).layout { measurable, constraints ->
                val extra = bleed.roundToPx() * 2
                val placeable = measurable.measure(Constraints.fixedWidth(constraints.maxWidth + extra))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
            },
    )
}
