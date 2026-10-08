package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.CheckboxRow
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.PeekingListSheet
import com.binge.designsystem.R as DesR

/**
 * TMDB keywords as a list row: the ones chosen, named once their names are read, else counted. A tap opens a peeking
 * sheet with a search field: blank, it lists what is chosen; typed, it lists what TMDB matches. Each tick applies at
 * once through [onToggle]. The names and the search are read only while the sheet is open ([onOpen], [onQuery]).
 */
@Composable
internal fun keywordSettingItem(
    icon: ImageVector,
    title: String,
    chosen: List<Int>,
    search: KeywordSearch,
    enabled: Boolean,
    onOpen: () -> Unit,
    onQuery: (String) -> Unit,
    onToggle: (Int) -> Unit,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open) {
        var query by rememberSaveable { mutableStateOf("") }
        PeekingListSheet(
            title = title,
            onDismiss = {
                open = false
                onQuery("")
            },
        ) {
            BingeSearchField(
                query = query,
                onQueryChange = {
                    query = it
                    onQuery(it)
                },
                onClear = {
                    query = ""
                    onQuery("")
                },
                placeholder = stringResource(R.string.server_settings_blocklist_tags_search),
                modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
            )
            KeywordChecklist(chosen, search, searching = query.isNotBlank(), onToggle = onToggle)
        }
    }
    val names = chosen.mapNotNull { search.names[it] }
    return ListItem(
        icon = icon,
        label = title,
        detail =
            when {
                chosen.isEmpty() -> stringResource(R.string.server_settings_blocklist_tags_none)
                names.size == chosen.size -> names.joinToString(", ")
                else -> pluralStringResource(R.plurals.server_settings_blocklist_tags_count, chosen.size, chosen.size)
            },
        clickable = enabled,
        disabled = !enabled,
        onClick = {
            open = true
            onOpen()
        },
    )
}

/** The sheet's list: the chosen keywords while nothing is typed, else the search's results with the chosen ones ticked. */
@Composable
private fun KeywordChecklist(
    chosen: List<Int>,
    search: KeywordSearch,
    searching: Boolean,
    onToggle: (Int) -> Unit,
) {
    val rows =
        if (searching) {
            search.results.orEmpty().map { it.id to it.name }
        } else {
            chosen.map { id -> id to (search.names[id] ?: id.toString()) }
        }
    when {
        searching && search.searching && search.results == null -> ListLoading()
        searching && search.failed -> Note(stringResource(R.string.server_settings_blocklist_tags_failed))
        searching && search.results?.isEmpty() == true -> Note(stringResource(R.string.server_settings_blocklist_tags_no_match))
        !searching && chosen.isEmpty() -> Note(stringResource(R.string.server_settings_blocklist_tags_empty))
    }
    rows.forEachIndexed { index, (id, name) ->
        CheckboxRow(label = name, checked = id in chosen, onToggle = { onToggle(id) }, showDivider = index < rows.lastIndex)
    }
}

@Composable
private fun Note(text: String) =
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
    )
