package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.MessageScreen
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.SearchResultRow
import kotlinx.coroutines.launch
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
 * The automatic blocklist's tags as a page: Material's search bar over the tags the server blocklists, each a chip that
 * removes it, or an empty state when there are none. The search opens full screen with TMDB's matches, each added or
 * taken out with a tap. Every change saves on its own (#930); a failed save says so in a snackbar that stays until
 * Retry, or until the next change sends the tags again.
 */
@Composable
fun BlocklistTagsScreen(
    state: BlocklistTagsUiState,
    actions: BlocklistTagsActions,
) {
    val snackbar = remember { SnackbarHostState() }
    val ready = state as? BlocklistTagsUiState.Ready
    SaveFailedSnackbar(ready?.saveFailed == true, snackbar, actions.onRetry)
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
                val search = it.search.copy(names = it.names)
                TagSearch(it.tags, search, actions.onSearch, actions.onToggle)
                ChosenTags(it.tags, search, actions.onToggle, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChosenTags(
    chosen: List<Int>,
    search: KeywordSearch,
    onRemove: (Int) -> Unit,
    modifier: Modifier,
) {
    if (chosen.isEmpty()) {
        MessageScreen(
            headline = stringResource(R.string.server_settings_blocklist_tags_empty_title),
            body = stringResource(R.string.server_settings_blocklist_tags_empty),
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
                InputChip(
                    selected = false,
                    onClick = { onRemove(id) },
                    label = { Text(name, style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.height(dimensionResource(R.dimen.tag_chip_height)),
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Sell,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(dimensionResource(R.dimen.tag_chip_icon)),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.server_settings_blocklist_tags_remove, name),
                            modifier = Modifier.size(dimensionResource(R.dimen.tag_chip_icon)),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
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

/**
 * Material's search bar over the tags: collapsed, a field that opens the full-screen search; expanded, TMDB's matches
 * under the field, each added or taken out with a tap. Back, or the arrow, closes it again onto the chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSearch(
    chosen: List<Int>,
    search: KeywordSearch,
    onQuery: (String) -> Unit,
    onToggle: (Int) -> Unit,
) {
    val barState = rememberSearchBarState()
    val text = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect(onQuery) }
    val input: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = barState,
            onSearch = {},
            placeholder = { Text(stringResource(R.string.server_settings_blocklist_tags_search)) },
            leadingIcon = {
                if (barState.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = { scope.launch { barState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (text.text.isNotEmpty()) {
                    IconButton(onClick = { text.clearText() }) { Icon(Icons.Filled.Close, contentDescription = null) }
                }
            },
        )
    }
    SearchBar(
        state = barState,
        inputField = input,
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
    )
    ExpandedFullScreenSearchBar(state = barState, inputField = input) {
        if (text.text.isBlank()) {
            Note(stringResource(R.string.server_settings_blocklist_tags_type), Modifier)
        } else {
            SearchResults(chosen, search, onToggle, Modifier.fillMaxSize())
        }
    }
}

/**
 * A failed save as a snackbar that stays until it is answered: Retry sends the tags as they stand. It goes by itself once
 * a later change starts the next save, which carries the failed changes with it.
 */
@Composable
private fun SaveFailedSnackbar(
    failed: Boolean,
    snackbar: SnackbarHostState,
    onRetry: () -> Unit,
) {
    val message = stringResource(R.string.server_settings_blocklist_tags_save_failed)
    val retry = stringResource(R.string.server_settings_blocklist_tags_retry)
    LaunchedEffect(failed) {
        if (!failed) {
            snackbar.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        val result = snackbar.showSnackbar(message, actionLabel = retry, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) onRetry()
    }
}
