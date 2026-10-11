package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeSearchField
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The keywords picked, under a search field. Blank, it shows the picks, each a tag that removes itself, or the empty
 * state; typed, it shows TMDB's matches for [onQuery], each added or taken out with a tap. The blocklisted tags page and
 * the rule's keyword picker both use it.
 *
 * [contentPadding] is the page's insets. The search field is a pinned header, so it sits below the top inset; the picks
 * and matches scroll under the bottom one rather than stopping at its edge.
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
    contentPadding: PaddingValues = PaddingValues(),
) {
    val direction = LocalLayoutDirection.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    LaunchedEffect(query) { onQuery(query) }
    Column(
        modifier =
            modifier.padding(
                start = contentPadding.calculateStartPadding(direction),
                end = contentPadding.calculateEndPadding(direction),
            ),
    ) {
        BingeSearchField(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" },
            placeholder = stringResource(R.string.server_settings_blocklist_tags_search),
            modifier =
                Modifier
                    .padding(top = contentPadding.calculateTopPadding())
                    .padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        )
        if (query.isBlank()) {
            ChosenTags(
                chosen = chosen,
                search = search,
                onRemove = onToggle,
                emptyTitle = emptyTitle,
                emptyBody = emptyBody,
                bottomInset = contentPadding.calculateBottomPadding(),
                modifier = Modifier.weight(1f),
            )
        } else {
            SearchResults(chosen, search, onToggle, contentPadding.calculateBottomPadding(), Modifier.weight(1f))
        }
    }
}
