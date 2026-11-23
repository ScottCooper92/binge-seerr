package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.CheckboxRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.request.ItemSelection
import com.cooper.binge.companion.seerr.ui.discover.title.label
import com.binge.designsystem.R as DesR

/**
 * A collection's films as a page of the sheet, with its own Back and a pinned Done. A film the server holds, has a request for or has
 * blocked is shown with its status and cannot be ticked, and the quota caps how many can be.
 */
@Composable
internal fun RequestFilmsPage(
    films: List<RequestFilm>,
    selection: ItemSelection,
    use4k: Boolean,
    enabled: Boolean,
    actions: RequestSheetActions,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        BingeSheetPageHeader(onBack = onBack, title = stringResource(R.string.request_sheet_films))
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        ) {
            selection.limit?.takeIf { it > 0 }?.let { limit ->
                Text(
                    pluralStringResource(R.plurals.request_sheet_films_limit, limit, limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selection.openCount > 1) {
                CheckboxRow(
                    label = stringResource(R.string.request_sheet_all_films),
                    checked = selection.allOpenPicked,
                    onCheckedChange = { actions.onAllFilms() },
                    enabled = enabled && selection.canPickAll,
                    icon = Icons.Filled.DoneAll,
                    showDivider = true,
                )
            }
            films.forEachIndexed { index, film ->
                val picked = film.tmdbId in selection.picked
                CheckboxRow(
                    label = film.title,
                    checked = picked || !film.open(use4k),
                    onCheckedChange = { actions.onFilm(film.tmdbId) },
                    enabled = enabled && film.open(use4k) && (picked || selection.canPickMore),
                    supportingText = listOfNotNull(film.availability(use4k).label()?.first, film.year).joinToString(" · ").ifBlank { null },
                    supportingMaxLines = 2,
                    icon = Icons.Filled.Movie,
                    showDivider = index != films.lastIndex,
                )
            }
        }
        BingeActionFooter(label = stringResource(R.string.request_sheet_seasons_done), onClick = onBack)
    }
}

/** "Request 3 films" on the pinned button. */
@Composable
internal fun filmsSubmitLabel(count: Int): String = pluralStringResource(R.plurals.request_sheet_submit_films, count, count)
