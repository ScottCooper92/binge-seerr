package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeStatusButton
import com.binge.designsystem.component.BingeStatusButtonEmphasis
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.core.ui.R as UiR

/** The watchlist button's state: [onList] is whether the title is on the user's watchlist, and [onToggle] flips it. Null where the server has no watchlist. */
class WatchlistButton(
    val onList: Boolean,
    val onToggle: () -> Unit,
)

/**
 * The state as the design system's status button: Request, filled with its icon, where nothing is held, and otherwise the state with
 * its status dot, tonal. It is enabled in every state, because a tap always opens a sheet.
 */
@Composable
fun PrimaryAction(
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
        icon = primary.state.icon(),
        emphasis = if (request) BingeStatusButtonEmphasis.Filled else BingeStatusButtonEmphasis.Tonal,
    )
}

@StringRes
private fun TitlePrimary.label() = if (state == TitleState.Request && fourKOnly) R.string.title_page_request_4k else state.labelRes()

/** Only Request carries an icon; every other state says itself with its status dot. */
fun TitleState.icon(): ImageVector? = if (this == TitleState.Request) Icons.Filled.Add else null

/** The colour of the status dot: the one the title's cards use, and red for Declined, which only the page knows. */
fun TitleState.sentiment() =
    when (this) {
        TitleState.Declined -> BingeSentiment.Negative
        else -> toAvailability().statusStyle()?.sentiment ?: BingeSentiment.Neutral
    }

fun TitleState.labelRes() =
    when (this) {
        TitleState.Declined -> UiR.string.request_state_declined
        else -> toAvailability().statusStyle()?.label ?: R.string.title_page_request
    }
