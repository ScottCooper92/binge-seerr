package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailRoundAction
import com.cooper.binge.companion.seerr.feature.discover.R

/**
 * The round buttons beside the primary, at most [ROUND_SLOTS], in a fixed order: Watchlist where the server has one, Manage once
 * Watch has taken the primary, Services where there is something to show, then [extras], the title's other actions that fit.
 */
@Composable
fun titleRoundActions(
    watchlist: WatchlistButton?,
    onManage: (() -> Unit)?,
    onServices: (() -> Unit)? = null,
    extras: List<DetailRoundAction> = emptyList(),
): List<DetailRoundAction> {
    val watchlistLabel = stringResource(R.string.title_page_action_watchlist)
    val watchlistDescription =
        stringResource(if (watchlist?.onList == true) R.string.title_page_watchlist_remove else R.string.title_page_watchlist_add)
    val manageLabel = stringResource(R.string.title_page_action_manage)
    val servicesLabel = stringResource(R.string.title_page_action_services)
    return listOfNotNull(
        watchlist?.let {
            DetailRoundAction(
                icon = if (it.onList) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                label = watchlistLabel,
                onClick = it.onToggle,
                contentDescription = watchlistDescription,
            )
        },
        onManage?.let { DetailRoundAction(Icons.Filled.Tune, manageLabel, it) },
        onServices?.let { DetailRoundAction(Icons.Filled.Storage, servicesLabel, it) },
    ) + extras
}
