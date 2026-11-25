package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.binge.designsystem.component.DetailRoundAction
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.flags.LocalWatchPlayer
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer

/** What a title page's actions are drawn from, whichever page it is. */
class TitleChromeFacts(
    val title: String,
    val webUrl: String,
    val kind: TitleKind,
    val tmdbId: Int,
    val watchUrl: String?,
    val mediaServerName: String?,
    val mediaServer: SeerrMediaServer?,
    val trailerUrl: String?,
    val onWatchlist: Boolean,
    val mayWatchlist: Boolean,
    val mayBlock: Boolean,
    /** 4K, or the remaining seasons, may still be requested: Manage has a row even with no request of the viewer's. */
    val moreToRequest: Boolean,
    val requestCount: Int,
)

/** The page's primary, round buttons, bar actions and the sheets they open, for the film and series templates alike. */
class TitlePageSlots(
    val primary: @Composable (Modifier) -> Unit,
    val roundActions: List<DetailRoundAction>,
    val topBarActions: @Composable RowScope.(Float) -> Unit,
    val overlays: @Composable () -> Unit,
)

/**
 * The title page's actions: Watch as the primary once the title is in the library, with Manage beside it, otherwise the status
 * primary; Watchlist, Manage and Services in the round buttons, then whichever of Trailer, Report, Share and Block fit; Report in the
 * bar if it did not, and More for the rest.
 */
@Composable
fun rememberTitlePageSlots(
    facts: TitleChromeFacts,
    actionsModel: TitleActionsModel,
    services: List<ServiceGroup>,
    actions: TitlePageActions,
): TitlePageSlots {
    val context = LocalContext.current
    val player = LocalWatchPlayer.current
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    val watch =
        rememberTitleWatch(
            actionsModel.primary.state,
            facts.watchUrl,
            facts.mediaServerName,
            facts.mediaServer,
            services,
            facts.kind,
            facts.tmdbId,
            actions.onWatchInBinge,
            actions.onOpenUrl,
        )
    val manageHasContent = actionsModel.primary.tap is PrimaryTap.ManageRequest || facts.moreToRequest || facts.requestCount > 1
    val watchlist = WatchlistButton(facts.onWatchlist, actions.onToggleWatchlist).takeIf { facts.mayWatchlist }
    val onManage = actions.onRequest.takeIf { watch != null && manageHasContent }
    val onServices = actions.onOpenServiceInfo.takeIf { services.isNotEmpty() }
    val spill =
        TitleSpill(
            onTrailer = facts.trailerUrl?.let { url -> { actions.onOpenUrl(url) } },
            onReport = actions.onReport.takeIf { actionsModel.canReport },
            onShare = { context.shareTitle(facts.title, facts.webUrl) },
            onBlock = actions.block?.onAsk.takeIf { facts.mayBlock && actionsModel.canBlock },
        )
    val placement = placeSpill(listOfNotNull(watchlist, onManage, onServices).size, spill)
    return TitlePageSlots(
        primary = { modifier ->
            if (watch != null) {
                WatchPrimary({ watch.play(context, player) { chooserOpen = true } }, modifier)
            } else {
                PrimaryAction(actionsModel.primary, actions.onRequest, modifier)
            }
        },
        roundActions = titleRoundActions(watchlist, onManage, onServices, spillRoundActions(spill, placement)),
        topBarActions = { glassAlpha ->
            TitleTopBarActions(glassAlpha, placement.barReport(spill), ({ moreOpen = true }).takeIf { placement.hasMore(spill) })
        },
        overlays = {
            if (moreOpen) {
                TitleMoreSheet(
                    onTrailer = placement.moreTrailer(spill),
                    onShare = placement.moreShare(spill),
                    onBlock = placement.moreBlock(spill),
                    onDismiss = { moreOpen = false },
                )
            }
            if (chooserOpen) watch?.let { WatchChoice(it) { chooserOpen = false } }
        },
    )
}
