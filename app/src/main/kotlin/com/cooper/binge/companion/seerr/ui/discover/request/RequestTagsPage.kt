package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.CheckboxRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.binge.designsystem.R as DesR

/** The server's tags as a checklist page of the sheet, with its own Back and a pinned Done, as the seasons page is. */
@Composable
internal fun RequestTagsPage(
    tags: List<RequestChoice>,
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Row(Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_s)), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.request_sheet_back))
            }
            Text(stringResource(R.string.request_tags), style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        ) {
            tags.forEachIndexed { index, tag ->
                CheckboxRow(
                    label = tag.label,
                    checked = tag.id in selected,
                    onCheckedChange = { onToggle(tag.id) },
                    enabled = enabled,
                    showDivider = index < tags.lastIndex,
                )
            }
        }
        BingeActionFooter(label = stringResource(R.string.request_sheet_seasons_done), onClick = onBack)
    }
}
