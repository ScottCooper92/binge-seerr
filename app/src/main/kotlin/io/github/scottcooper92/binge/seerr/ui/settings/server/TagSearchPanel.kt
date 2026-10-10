package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeSearchField
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The keywords picked, under a search field. Blank, it shows the picks, each a tag that removes itself, or the empty
 * state; typed, it shows TMDB's matches for [onQuery], each added or taken out with a tap. The blocklisted tags page and
 * the rule's keyword picker both use it.
 */
@Composable
internal fun TagSearchPanel(
    chosen: List<Int>,
    search: KeywordSearch,
    onQuery: (String) -> Unit,
    onToggle: (Int) -> Unit,
    emptyTitle: String,
    emptyBody: String,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    Column(modifier = modifier) {
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
        if (query.isBlank()) {
            ChosenTags(
                chosen = chosen,
                search = search,
                onRemove = onToggle,
                emptyTitle = emptyTitle,
                emptyBody = emptyBody,
                modifier = Modifier.weight(1f),
            )
        } else {
            SearchResults(chosen, search, onToggle, Modifier.weight(1f))
        }
    }
}
