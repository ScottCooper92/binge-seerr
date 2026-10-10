package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.template.FormScreen
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.SearchResultRow
import com.binge.designsystem.R as DesR

/**
 * TMDB's company search, full screen over the slider being edited, for its one studio: a match is picked with a tap,
 * through [onPick], and the dialog closes. Nothing is saved until the slider is. Back closes it unchanged.
 */
@Composable
internal fun CompanyPickerDialog(
    title: String,
    chosen: Int?,
    search: CompanySearch,
    onSearch: (String) -> Unit,
    onPick: (Company) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            FormScreen(title = title, onBack = onDismiss, scrolling = false) { inner ->
                var query by rememberSaveable { mutableStateOf("") }
                LaunchedEffect(query) { onSearch(query) }
                Column(modifier = Modifier.fillMaxSize().padding(inner)) {
                    BingeSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onClear = { query = "" },
                        placeholder = stringResource(R.string.server_settings_slider_studio_search),
                        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
                    )
                    CompanyResults(query, chosen, search, onPick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompanyResults(
    query: String,
    chosen: Int?,
    search: CompanySearch,
    onPick: (Company) -> Unit,
    modifier: Modifier,
) {
    val results = search.results
    when {
        query.isBlank() -> Note(stringResource(R.string.server_settings_slider_studio_type), modifier)
        search.failed -> Note(stringResource(R.string.server_settings_slider_studio_failed), modifier)
        results == null ->
            Box(
                modifier = modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_l)),
                contentAlignment = Alignment.TopCenter,
            ) {
                BingeLoadingIndicator()
            }
        results.isEmpty() -> Note(stringResource(R.string.server_settings_slider_studio_no_match), modifier)
        else ->
            Column(modifier = modifier.verticalScroll(rememberScrollState())) {
                results.forEachIndexed { index, company ->
                    SearchResultRow(
                        title = company.name,
                        added = company.id == chosen,
                        onToggle = { onPick(company) },
                        icon = Icons.Filled.Business,
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
