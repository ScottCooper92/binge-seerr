package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.tv.template.TvPaneOption
import com.binge.designsystem.tv.template.TvPaneRow

/**
 * A switch as a pane row: a multi-select row with one option, so the option's `selected` is where the switch stands and
 * pressing it flips it. The sheet draws such a row with a switch at its end, which says on or off in place of any words.
 */
internal fun switchRow(
    key: String,
    label: String,
    on: Boolean,
    icon: ImageVector,
    body: String = "",
    onToggle: () -> Unit,
): TvPaneRow =
    TvPaneRow(
        key = key,
        label = label,
        body = body,
        options = listOf(TvPaneOption(label = label, selected = on, onSelect = onToggle, icon = icon)),
        icon = icon,
        multiSelect = true,
    )

/** Where [this] stands if it is a [switchRow], else null. */
internal fun TvPaneRow.switchedOn(): Boolean? = options.singleOrNull()?.selected?.takeIf { multiSelect }
