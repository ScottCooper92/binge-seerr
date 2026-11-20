package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailRoundAction
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The round buttons beside the primary, as the design system's detail page takes them: Trailer (opens outside the app, so it
 * carries the open-in-new mark), Watchlist where the server has one, Report once the title is in the library, and Block while
 * nothing is held. A button the page has no handler for, or the state does not allow, is left out.
 */
@Composable
internal fun titleRoundActions(
    model: TitleActionsModel,
    onTrailer: (() -> Unit)?,
    watchlist: WatchlistButton?,
    onReport: (() -> Unit)?,
    onBlock: (() -> Unit)?,
): List<DetailRoundAction> {
    val trailerLabel = stringResource(R.string.title_page_action_trailer)
    val trailerDescription = stringResource(DesR.string.cd_link_chip_opens_outside, trailerLabel)
    val watchlistLabel = stringResource(R.string.title_page_action_watchlist)
    val watchlistDescription =
        stringResource(if (watchlist?.onList == true) R.string.title_page_watchlist_remove else R.string.title_page_watchlist_add)
    val reportLabel = stringResource(R.string.title_page_action_report)
    val blockLabel = stringResource(R.string.title_page_action_block)
    return listOfNotNull(
        onTrailer?.let {
            DetailRoundAction(
                icon = Icons.Filled.PlayArrow,
                label = trailerLabel,
                onClick = it,
                contentDescription = trailerDescription,
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
            )
        },
        watchlist?.let {
            DetailRoundAction(
                icon = if (it.onList) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = watchlistLabel,
                onClick = it.onToggle,
                contentDescription = watchlistDescription,
            )
        },
        onReport?.takeIf { model.canReport }?.let { DetailRoundAction(Icons.Filled.ReportProblem, reportLabel, it) },
        onBlock?.takeIf { model.canBlock }?.let { DetailRoundAction(Icons.Filled.Block, blockLabel, it) },
    )
}
