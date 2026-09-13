package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvIllustration
import com.binge.designsystem.tv.focus.TvStableFocusScroll
import com.binge.designsystem.tv.focus.tvEntryFocusGroup
import com.binge.designsystem.tv.focus.tvFocusGroup
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

private const val LIST_WEIGHT = 0.34f
private const val PANE_WEIGHT = 0.66f

/** One option in a row's pane: a label, a tick when current, and the call that commits it. */
@Immutable
internal data class TvPaneOption(
    val label: String,
    val selected: Boolean = false,
    val onSelect: () -> Unit = {},
)

/**
 * A row of the list/pane board: its name on the left, and on the right what it means, its options and a
 * closing note. No options means a read-out: the value lives in [body] and [note] says where to change it.
 */
@Immutable
internal data class TvPaneRow(
    val key: String,
    val label: String,
    val body: String,
    val note: String? = null,
    val options: List<TvPaneOption> = emptyList(),
    val icon: ImageVector? = null,
)

/** A titled block of rows; headers are read-outs and never take focus. */
@Immutable
internal data class TvPaneGroup(
    val title: String,
    val rows: List<TvPaneRow>,
)

/**
 * The list/pane board Binge's TV settings take: names on the left, the described row's meaning and options
 * on the right. Stateless — [focusedKey] names the row the pane describes, not whether it holds focus, and
 * the list derives that itself so the described row dims once focus moves into the pane.
 *
 * ← from an option returns to the described row through the list's entry group, which redirects focus
 * arriving from outside to that row; the pane's `left` override is the belt to that brace.
 */
@Composable
internal fun TvListPaneBoard(
    title: String,
    groups: List<TvPaneGroup>,
    focusedKey: String?,
    onFocusRow: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialListHasFocus: Boolean = false,
    initialFocusedOptionLabel: String? = null,
) {
    val describedRow = groups.firstNotNullOfOrNull { group -> group.rows.firstOrNull { it.key == focusedKey } }
    val focusedRowFocus = remember { FocusRequester() }
    TvBoardFrame(title = title, modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize()) {
            TvPaneList(
                groups = groups,
                focusedKey = describedRow?.key,
                onFocusRow = onFocusRow,
                focusedRowFocus = focusedRowFocus,
                modifier = Modifier.weight(LIST_WEIGHT),
                initialColumnHasFocus = initialListHasFocus,
            )
            TvBoardDivider()
            TvPane(
                row = describedRow,
                backToListFocus = focusedRowFocus,
                modifier = Modifier.weight(PANE_WEIGHT).padding(start = dimensionResource(R.dimen.tv_pane_gap)),
                initialFocusedOptionLabel = initialFocusedOptionLabel,
            )
        }
    }
}

@Composable
private fun TvPaneList(
    groups: List<TvPaneGroup>,
    focusedKey: String?,
    onFocusRow: (String) -> Unit,
    focusedRowFocus: FocusRequester,
    modifier: Modifier = Modifier,
    initialColumnHasFocus: Boolean = false,
) {
    var columnHasFocus by remember { mutableStateOf(initialColumnHasFocus) }
    TvStableFocusScroll {
        Column(
            modifier =
                modifier
                    .fillMaxHeight()
                    .onFocusChanged { columnHasFocus = it.hasFocus }
                    .tvEntryFocusGroup(focusedRowFocus)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        end = dimensionResource(R.dimen.tv_pane_gap),
                        top = dimensionResource(TvR.dimen.tv_focus_ring_bleed),
                        bottom = dimensionResource(TvR.dimen.tv_focus_ring_bleed),
                    ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_pane_row_gap)),
        ) {
            groups.forEach { group ->
                Text(
                    text = group.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            start = dimensionResource(R.dimen.tv_list_row_padding_horizontal),
                            top = dimensionResource(DesR.dimen.padding_m),
                            bottom = dimensionResource(DesR.dimen.padding_xs),
                        ),
                )
                group.rows.forEach { row ->
                    val described = row.key == focusedKey
                    TvPaneListRow(
                        label = row.label,
                        // The described row keeps a quiet fill while focus is over in the pane, so the eye still
                        // knows which setting the options belong to; while the column holds focus, focus says it.
                        describedWhileAway = described && !columnHasFocus,
                        onFocused = { onFocusRow(row.key) },
                        modifier = if (described) Modifier.focusRequester(focusedRowFocus) else Modifier,
                    )
                }
            }
        }
    }
}

/** A one-line list item: the setting's name, since the pane carries its value. Focus is colour, never scale. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPaneListRow(
    label: String,
    describedWhileAway: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = onFocused,
        headlineContent = { Text(text = label, style = MaterialTheme.typography.titleMedium) },
        shape = ListItemDefaults.shape(shape = BingeShapes.TvListItem),
        colors =
            ListItemDefaults.colors(
                containerColor = if (describedWhileAway) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        scale = ListItemScale.None,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocused() },
    )
}

/** The right pane: an illustration, the row's name, what it means, its options, and a closing note. */
@Composable
private fun TvPane(
    row: TvPaneRow?,
    backToListFocus: FocusRequester,
    modifier: Modifier = Modifier,
    initialFocusedOptionLabel: String? = null,
) {
    if (row == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }
    TvStableFocusScroll {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = dimensionResource(TvR.dimen.tv_focus_ring_bleed)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TvIllustration(icon = row.icon ?: Icons.Filled.Settings)
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = row.body,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (row.options.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .width(dimensionResource(R.dimen.tv_pane_option_width))
                            .padding(top = dimensionResource(DesR.dimen.padding_s))
                            .focusProperties { left = backToListFocus }
                            .tvFocusGroup(),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_form_option_gap)),
                ) {
                    row.options.forEach { option ->
                        TvOptionRow(
                            label = option.label,
                            selected = option.selected,
                            onSelect = option.onSelect,
                            initiallyFocused = option.label == initialFocusedOptionLabel,
                        )
                    }
                }
            }
            row.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_xs)),
                )
            }
        }
    }
}
