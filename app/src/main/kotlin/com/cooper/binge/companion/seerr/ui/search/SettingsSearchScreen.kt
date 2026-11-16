package com.cooper.binge.companion.seerr.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/**
 * The search page: a field, focused as it opens, and the pages the query finds among those this viewer may open, each with where
 * it is. A result opens through [open].
 *
 * @param initialQuery what the field starts with; a still frame's way to show results.
 */
@Composable
internal fun SettingsSearchScreen(
    state: SettingsSearchUiState,
    onRetry: () -> Unit,
    open: (SettingEntry) -> Unit,
    onBack: () -> Unit,
    initialQuery: String = "",
) {
    BingeScreenScaffold(title = stringResource(R.string.search_title), bar = ScreenBar.Small, onBack = onBack) { padding ->
        ScreenBody(padding) { inner ->
            when (state) {
                SettingsSearchUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                SettingsSearchUiState.Error -> ErrorScreen(SeerrError.Unreachable, Modifier.padding(inner), onRetry)
                is SettingsSearchUiState.Ready -> SearchBody(state.access, open, inner, initialQuery)
            }
        }
    }
}

@Composable
private fun SearchBody(
    access: SearchAccess,
    open: (SettingEntry) -> Unit,
    inner: PaddingValues,
    initialQuery: String,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val found =
        remember(query, access) {
            searchSettings(query, settingsIndex, { it.available(access) }, context::getString)
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        BingeSearchField(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" },
            placeholder = stringResource(R.string.search_title),
            focusRequester = focus,
            modifier = Modifier.padding(resolvedContentPadding(vertical = dimensionResource(DesR.dimen.padding_s))),
        )
        when {
            query.isBlank() ->
                EmptyScreen(
                    stringResource(R.string.search_prompt),
                    Modifier.weight(1f),
                    title = stringResource(R.string.search_title),
                    icon = Icons.Filled.Search,
                )
            found.isEmpty() ->
                EmptyScreen(
                    stringResource(R.string.search_no_match),
                    Modifier.weight(1f),
                    title = stringResource(R.string.search_no_match_title),
                )
            else -> Results(found, open, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Results(
    found: List<SettingEntry>,
    open: (SettingEntry) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier) {
        item {
            ItemGroup(
                title = null,
                rows =
                    found.map { entry ->
                        ListItem(
                            icon = entry.icon,
                            label = stringResource(entry.label),
                            detail = entry.path(),
                            onClick = { open(entry) },
                        )
                    },
                modifier = Modifier.padding(resolvedContentPadding()),
            )
        }
    }
}

/** Where an entry is: its section, and for a setting its page too. */
@Composable
private fun SettingEntry.path(): String =
    if (pageLabel == null) {
        stringResource(section)
    } else {
        stringResource(R.string.search_path, stringResource(section), stringResource(pageLabel))
    }
