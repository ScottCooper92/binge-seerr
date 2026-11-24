package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.component.BingeSheetPageHeader
import com.cooper.binge.companion.seerr.ui.state.SortContent

/**
 * A picker swapped in for [EditRequestContent]'s own body rather than opened as a second bottom
 * sheet: [EditRequestSheet] is already one, and stacking a choice sheet
 * on top of it would be two windows and two scrims for what a pick closes right back out of — see #336.
 */
@Composable
fun <T> DestinationPickerPanel(
    icon: ImageVector,
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onBack: () -> Unit,
    filterPlaceholder: String? = null,
    noMatchLabel: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    // No scroll of its own: the list scrolls itself, and a scroll around it would hand it an unbounded height.
    Column {
        BingeSheetPageHeader(onBack = onBack, title = title)
        SortContent(
            title = null,
            choices = choices.map { it.first },
            selected = selected,
            label = { id -> choices.first { it.first == id }.second },
            icon = { icon },
            onSelect = onSelect,
            modifier = Modifier.weight(1f, fill = false),
            filterPlaceholder = filterPlaceholder,
            noMatchLabel = noMatchLabel,
        )
        footer?.invoke()
    }
}
