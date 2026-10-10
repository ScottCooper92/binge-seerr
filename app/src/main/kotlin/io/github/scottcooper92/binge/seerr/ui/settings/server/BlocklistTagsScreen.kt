package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.MessageScreen
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.SaveFailedSnackbar
import io.github.scottcooper92.binge.seerr.ui.state.SearchResultRow
import com.binge.designsystem.R as DesR

/** What the blocklisted tags page asks of its view model. */
class BlocklistTagsActions(
    val onBack: () -> Unit,
    val onSearch: (String) -> Unit,
    val onToggle: (Int) -> Unit,
    val onRetry: () -> Unit,
    val onReload: () -> Unit,
)

/**
 * The automatic blocklist's tags as a page: a search field over the tags the server blocklists, each a tag that removes
 * it, or an empty state when there are none. Typing swaps the tags for TMDB's matches, each added or taken out with a
 * tap. Every change saves on its own (#930); a failed save says so in a snackbar that stays until
 * Retry, or until the next change sends the tags again.
 */
@Composable
fun BlocklistTagsScreen(
    state: BlocklistTagsUiState,
    actions: BlocklistTagsActions,
) {
    val snackbar = remember { SnackbarHostState() }
    val ready = state as? BlocklistTagsUiState.Ready
    SaveFailedSnackbar(
        failed = ready?.saveFailed == true,
        snackbarHostState = snackbar,
        message = stringResource(R.string.server_settings_blocklist_tags_save_failed),
        retryLabel = stringResource(R.string.server_settings_blocklist_tags_retry),
        onRetry = actions.onRetry,
    )
    FormScreen(
        title = stringResource(R.string.server_settings_blocklist_tags),
        onBack = actions.onBack,
        snackbarHostState = snackbar,
        scrolling = false,
        notReady =
            when (state) {
                BlocklistTagsUiState.Loading -> { inner -> LoadingScreen(Modifier.padding(inner)) }
                is BlocklistTagsUiState.Error -> { inner -> ErrorScreen(state.error, Modifier.padding(inner), onRetry = actions.onReload) }
                is BlocklistTagsUiState.Ready -> null
            },
    ) { inner ->
        ready?.let {
            Column(modifier = Modifier.fillMaxSize().padding(inner)) {
                TagSearchPanel(
                    chosen = it.tags,
                    search = it.search,
                    onQuery = actions.onSearch,
                    onToggle = actions.onToggle,
                    emptyTitle = stringResource(R.string.server_settings_blocklist_tags_empty_title),
                    emptyBody = stringResource(R.string.server_settings_blocklist_tags_empty),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The keywords chosen, each a tag that removes it, or [emptyTitle] and [emptyBody] when there are none. */
@Composable
internal fun ChosenTags(
    chosen: List<Int>,
    search: KeywordSearch,
    onRemove: (Int) -> Unit,
    emptyTitle: String,
    emptyBody: String,
    modifier: Modifier,
) {
    if (chosen.isEmpty()) {
        MessageScreen(
            headline = emptyTitle,
            body = emptyBody,
            icon = Icons.Filled.Sell,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Text(
            pluralStringResource(R.plurals.server_settings_blocklist_tags_count, chosen.size, chosen.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            chosen.forEach { id ->
                val name = search.names[id] ?: id.toString()
                RemovableTag(name = name, onRemove = { onRemove(id) })
            }
        }
    }
}

@Composable
internal fun SearchResults(
    chosen: List<Int>,
    search: KeywordSearch,
    onToggle: (Int) -> Unit,
    modifier: Modifier,
) {
    val results = search.results
    when {
        search.failed -> Note(stringResource(R.string.server_settings_blocklist_tags_failed), modifier)
        results == null ->
            Box(
                modifier = modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_l)),
                contentAlignment = Alignment.TopCenter,
            ) {
                BingeLoadingIndicator()
            }
        results.isEmpty() -> Note(stringResource(R.string.server_settings_blocklist_tags_no_match), modifier)
        else ->
            Column(modifier = modifier.verticalScroll(rememberScrollState())) {
                results.forEachIndexed { index, keyword ->
                    SearchResultRow(
                        title = keyword.name,
                        added = keyword.id in chosen,
                        onToggle = { onToggle(keyword.id) },
                        icon = Icons.Filled.Sell,
                    )
                    if (index < results.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = dimensionResource(DesR.dimen.padding_m)),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
    }
}

@Composable
private fun Note(
    text: String,
    modifier: Modifier,
) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(dimensionResource(DesR.dimen.padding_m)),
)

/** One chosen keyword as the tag it is everywhere else, with a close mark: a tap takes it out. */
@Composable
private fun RemovableTag(
    name: String,
    onRemove: () -> Unit,
) {
    val label = stringResource(R.string.server_settings_blocklist_tags_remove, name)
    Row(
        modifier =
            Modifier
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .clickable(onClickLabel = label, role = Role.Button, onClick = onRemove),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        BingeTag(label = name, icon = Icons.Filled.Sell, uppercase = false)
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimensionResource(DesR.dimen.padding_m)),
        )
    }
}
