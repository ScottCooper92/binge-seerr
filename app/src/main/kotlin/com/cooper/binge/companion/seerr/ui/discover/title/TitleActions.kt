package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeStatusButton
import com.binge.designsystem.component.BingeStatusButtonEmphasis
import com.binge.designsystem.component.LabelledIconButton
import com.binge.designsystem.isExpandedLayout
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The actions under the stats: the primary, there in every state and saying where the title stands, then a round
 * Trailer button, a Watchlist button where the server has one, a Report button once the title is in the library and a Block button while nothing is held, each carrying a label. Share is in the page's bar only. [onTrailer] is null where the server lists no trailer, and the
 * button is not drawn.
 */
@Composable
internal fun TitleActions(
    model: TitleActionsModel,
    onPrimary: () -> Unit,
    onTrailer: (() -> Unit)?,
    onReport: (() -> Unit)?,
    modifier: Modifier = Modifier,
    watchlist: WatchlistButton? = null,
    onBlock: (() -> Unit)? = null,
) {
    val report = onReport?.takeIf { model.canReport }
    val block = onBlock?.takeIf { model.canBlock }
    val roundButtons = listOfNotNull(onTrailer, watchlist, report, block).size
    val gap = dimensionResource(DesR.dimen.padding_m)
    val edge = modifier.fillMaxWidth().padding(horizontal = gap)
    if (roundButtons >= STACK_FROM && !isExpandedLayout()) {
        Column(modifier = edge, verticalArrangement = Arrangement.spacedBy(gap)) {
            PrimaryAction(model.primary, onPrimary, Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top,
            ) {
                RoundButtons(onTrailer, watchlist, report, block)
            }
        }
    } else {
        Row(modifier = edge, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
            PrimaryAction(model.primary, onPrimary, Modifier.weight(1f))
            RoundButtons(onTrailer, watchlist, report, block)
        }
    }
}

/** From this many round buttons the primary takes a row of its own, so its label keeps the room it needs at a large font. */
private const val STACK_FROM = 2

@Composable
private fun RoundButtons(
    onTrailer: (() -> Unit)?,
    watchlist: WatchlistButton?,
    onReport: (() -> Unit)?,
    onBlock: (() -> Unit)?,
) {
    onTrailer?.let { trailer ->
        val label = stringResource(R.string.title_page_action_trailer)
        LabelledIconButton(
            Icons.Filled.PlayArrow,
            label,
            trailer,
            contentDescription = stringResource(DesR.string.cd_link_chip_opens_outside, label),
        )
    }
    watchlist?.let { WatchlistActionButton(it) }
    onReport?.let { LabelledIconButton(Icons.Filled.ReportProblem, stringResource(R.string.title_page_action_report), it) }
    onBlock?.let { LabelledIconButton(Icons.Filled.Block, stringResource(R.string.title_page_action_block), it) }
}

/** The watchlist button's state: [onList] is whether the title is on the user's watchlist, and [onToggle] flips it. Null where the server has no watchlist. */
internal class WatchlistButton(
    val onList: Boolean,
    val onToggle: () -> Unit,
)

/** A round bookmark, filled while the title is on the watchlist. Its announced name says what a tap does. */
@Composable
private fun WatchlistActionButton(watchlist: WatchlistButton) {
    LabelledIconButton(
        icon = if (watchlist.onList) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
        label = stringResource(R.string.title_page_action_watchlist),
        onClick = watchlist.onToggle,
        contentDescription =
            stringResource(if (watchlist.onList) R.string.title_page_watchlist_remove else R.string.title_page_watchlist_add),
    )
}

/**
 * The state as the design system's status button: Request, filled with its icon, where nothing is held, and otherwise the state with
 * its status dot, tonal. It is enabled in every state, because a tap always opens a sheet.
 */
@Composable
private fun PrimaryAction(
    primary: TitlePrimary,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val label = stringResource(primary.label())
    val request = primary.state == TitleState.Request
    BingeStatusButton(
        label = label,
        onClick = onClick,
        sentiment = primary.state.sentiment(),
        modifier = modifier.fillMaxWidth(),
        icon = if (request) Icons.Filled.Add else null,
        emphasis = if (request) BingeStatusButtonEmphasis.Filled else BingeStatusButtonEmphasis.Tonal,
    )
}

@StringRes
private fun TitlePrimary.label() = if (state == TitleState.Request && fourKOnly) R.string.title_page_request_4k else state.labelRes()

private fun TitleState.sentiment() =
    when (this) {
        TitleState.Declined -> BingeSentiment.Negative
        else -> toAvailability().statusStyle()?.sentiment ?: BingeSentiment.Neutral
    }

internal fun TitleState.labelRes() =
    when (this) {
        TitleState.Declined -> R.string.request_state_declined
        else -> toAvailability().statusStyle()?.label ?: R.string.title_page_request
    }
