package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeSheetPageHeader
import com.binge.designsystem.component.CheckboxRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.binge.designsystem.R as DesR
import com.cooper.binge.companion.seerr.core.ui.R as UiR

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
        BingeSheetPageHeader(onBack = onBack, title = stringResource(UiR.string.request_tags))
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
