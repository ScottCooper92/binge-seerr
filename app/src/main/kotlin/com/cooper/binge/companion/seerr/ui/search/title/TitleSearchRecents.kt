package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.TrendingSearchChip
import com.binge.designsystem.component.WrappingRow
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/** The queries searched before, newest first, as chips, with Clear behind a confirmation. A tap searches the query again. */
@Composable
internal fun RecentSearches(
    recents: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.title_search_recent),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirming = true }) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Text(
                    stringResource(R.string.title_search_recent_clear),
                    modifier = Modifier.padding(start = dimensionResource(DesR.dimen.padding_s)),
                )
            }
        }
        WrappingRow(
            horizontalSpacing = dimensionResource(DesR.dimen.padding_s),
            verticalSpacing = dimensionResource(DesR.dimen.padding_s),
        ) {
            recents.forEach { query ->
                TrendingSearchChip(label = query, onClick = { onSelect(query) }, leadingIcon = Icons.Filled.History)
            }
        }
    }
    if (confirming) {
        BingeConfirmDialog(
            title = stringResource(R.string.title_search_recent_clear_title),
            message = stringResource(R.string.title_search_recent_clear_message),
            confirmLabel = stringResource(R.string.title_search_recent_clear),
            destructive = true,
            onConfirm = {
                confirming = false
                onClear()
            },
            onDismissRequest = { confirming = false },
        )
    }
}
