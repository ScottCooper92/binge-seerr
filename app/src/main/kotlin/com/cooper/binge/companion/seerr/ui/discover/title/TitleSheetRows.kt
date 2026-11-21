package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.ui.requests.SheetTitleRow
import com.cooper.binge.companion.seerr.ui.requests.SiblingSheet

/** What the title offers beyond its status, as [rememberTitleSheetRows] reads it. */
internal class TitleSheetFacts(
    val state: TitleState,
    val watchUrl: String?,
    val mediaServerName: String?,
    val fourKOpen: Boolean,
    val remainingOpen: Boolean = false,
    val requests: List<com.cooper.binge.companion.seerr.discover.title.TitleRequest> = emptyList(),
)

/**
 * The rows the title adds to a request's sheet: Watch (in Binge where it answers, then on the media server) where the title is in the library and has a link, then Request in 4K
 * where the viewer may. One layout for a title with a request on file, as the status sheet is for one without.
 */
@Composable
internal fun rememberTitleSheetRows(
    facts: TitleSheetFacts,
    onWatch: (String) -> Unit,
    onRequestFourK: () -> Unit,
    onWatchInBinge: (() -> Unit)? = null,
    onRequestRemaining: () -> Unit = {},
    onOpenRequests: () -> Unit = {},
): List<SheetTitleRow> {
    val resources = LocalResources.current
    val watch by rememberUpdatedState(onWatch)
    val watchInBinge by rememberUpdatedState(onWatchInBinge)
    val requestFourK by rememberUpdatedState(onRequestFourK)
    val requestRemaining by rememberUpdatedState(onRequestRemaining)
    val openRequests by rememberUpdatedState(onOpenRequests)
    val bingeAnswers = onWatchInBinge != null
    val openInBinge: () -> Unit = { watchInBinge?.invoke() }
    return remember(facts.state, facts.watchUrl, facts.mediaServerName, facts.fourKOpen, facts.remainingOpen, facts.requests.size, bingeAnswers, resources) {
        val watchRows =
            titleWatchRows(
                facts.state,
                facts.watchUrl,
                facts.mediaServerName,
                onWatchInBinge = openInBinge.takeIf { bingeAnswers },
                onWatchOnServer = { watch(it) },
                resources = resources,
            ).map { row -> SheetTitleRow(Icons.Filled.PlayArrow, row.label, external = true, onClick = row.onClick) }
        watchRows +
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
                SheetTitleRow(
                    Icons.AutoMirrored.Filled.ListAlt,
                    resources.getString(R.string.title_sheet_requests, facts.requests.size),
                    external = false,
                    onClick = { openRequests() },
                ).takeIf { facts.requests.size > 1 },
            )
    }
}

/** A request's sheet over the title page, with the title's own rows above the request's. Both pages host it the same way. */
@Composable
internal fun TitleManageSheet(
    requestId: Int,
    open: Boolean,
    snackbar: SnackbarHostState,
    facts: TitleSheetFacts,
    actions: TitlePageActions,
) {
    actions.manageSheet(
        SiblingSheet(
            requestId,
            open = open,
            snackbarHostState = snackbar,
            onDismiss = actions.onCloseManage,
            onChanged = actions.onReload,
            titleRows =
                rememberTitleSheetRows(
                    facts,
                    onWatch = { url ->
                        actions.onCloseManage()
                        actions.onOpenUrl(url)
                    },
                    onRequestFourK = actions.onRequestFourK,
                    onRequestRemaining = actions.onRequestRemaining,
                    onOpenRequests = {
                        actions.onCloseManage()
                        actions.onOpenRequests()
                    },
                    onWatchInBinge =
                        actions.onWatchInBinge?.let { openInBinge ->
                            {
                                actions.onCloseManage()
                                openInBinge()
                            }
                        },
                ),
        ),
    )
}

internal fun SeriesDetails.sheetFacts() =
    TitleSheetFacts(
        titleActionsModel(standard, fourK, requests, mayReport).primary.state,
        watchUrl,
        mediaServerName,
        fourK?.mayRequest == true,
        remainingOpen = standard.mayRequest,
        requests = requests,
    )

internal fun MovieDetails.sheetFacts() =
    TitleSheetFacts(
        titleActionsModel(standard, fourK, requests, mayReport).primary.state,
        watchUrl,
        mediaServerName,
        fourK?.mayRequest == true,
        requests = requests,
    )
