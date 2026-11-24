package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.ui.requests.RequestCardsHost
import com.cooper.binge.companion.seerr.ui.requests.SheetScope
import com.cooper.binge.companion.seerr.ui.requests.SheetStateChip
import com.cooper.binge.companion.seerr.ui.requests.SheetTitleRow
import com.cooper.binge.companion.seerr.ui.requests.SiblingSheet

/** What the title offers beyond its status, as [rememberTitleSheetRows] reads it. */
internal class TitleSheetFacts(
    val state: TitleState,
    val fourKOpen: Boolean,
    val remainingOpen: Boolean = false,
)

/**
 * The rows the title adds to its Manage sheet: what more the viewer may request. The remaining seasons of a series in the library,
 * then Request in 4K where the viewer may. Watch is the page's primary and Report its own action, so neither is here.
 */
@Composable
internal fun rememberTitleSheetRows(
    facts: TitleSheetFacts,
    onRequestFourK: () -> Unit,
    onRequestRemaining: () -> Unit = {},
): List<SheetTitleRow> {
    val resources = LocalResources.current
    val requestFourK by rememberUpdatedState(onRequestFourK)
    val requestRemaining by rememberUpdatedState(onRequestRemaining)
    return remember(facts.state, facts.fourKOpen, facts.remainingOpen, resources) {
        listOfNotNull(
            SheetTitleRow(
                Icons.Filled.Add,
                resources.getString(R.string.title_sheet_request_remaining),
                external = false,
                onClick = { requestRemaining() },
            ).takeIf { facts.remainingOpen && facts.state.isInLibrary() },
            SheetTitleRow(
                Icons.Filled.Add,
                resources.getString(R.string.request_sheet_submit_4k),
                external = false,
                onClick = { requestFourK() },
            ).takeIf { facts.fourKOpen },
        )
    }
}

/** A request's sheet over the title page, with the title's own rows above the requests'. Both pages host it the same way. */
@Composable
internal fun TitleManageSheet(
    requestId: Int,
    open: Boolean,
    snackbar: SnackbarHostState,
    facts: TitleSheetFacts,
    actions: TitlePageActions,
    fromList: Boolean = false,
    scope: SheetScope = SheetScope.Both,
) {
    actions.manageSheet(
        SiblingSheet(
            requestId,
            open = open,
            snackbarHostState = snackbar,
            onDismiss = actions.onCloseManage,
            onChanged = actions.onReload,
            titleBack = actions.onBackToRequests.takeIf { fromList },
            scope = scope,
            cards = RequestCardsHost(actions.onPickRequest, actions.onReload, snackbar).takeIf { scope == SheetScope.Title },
            titleState = SheetStateChip(stringResource(facts.state.labelRes()), facts.state.sentiment()),
            titleRows =
                rememberTitleSheetRows(
                    facts,
                    onRequestFourK = actions.onRequestFourK,
                    onRequestRemaining = actions.onRequestRemaining,
                ),
        ),
    )
}

internal fun SeriesDetails.sheetFacts() =
    TitleSheetFacts(
        titleActionsModel(standard, fourK, requests, mayReport).primary.state,
        fourKOpen = fourK?.mayRequest == true,
        remainingOpen = standard.mayRequest,
    )

internal fun MovieDetails.sheetFacts() =
    TitleSheetFacts(
        titleActionsModel(standard, fourK, requests, mayReport).primary.state,
        fourKOpen = fourK?.mayRequest == true,
    )
