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
import com.cooper.binge.companion.seerr.discover.request.SeasonSelection
import com.cooper.binge.companion.seerr.feature.discover.R
import com.binge.designsystem.R as DesR

/**
 * The season checklist as a page of the sheet, with its own Back and a pinned Done. It keeps every rule of the checklist: covered
 * seasons are shown and cannot be picked, partial requests off means the whole show, and the quota caps how many can be ticked.
 */
@Composable
fun RequestSeasonPage(
    selection: SeasonSelection,
    enabled: Boolean,
    actions: RequestSheetActions,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        BingeSheetPageHeader(onBack = onBack, title = stringResource(R.string.request_sheet_seasons))
        Column(
            Modifier
                .weight(
                    1f,
                    fill = false,
                ).verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        ) {
            SeasonStep(selection, enabled, actions)
        }
        BingeActionFooter(label = stringResource(R.string.request_sheet_seasons_done), onClick = onBack)
    }
}
