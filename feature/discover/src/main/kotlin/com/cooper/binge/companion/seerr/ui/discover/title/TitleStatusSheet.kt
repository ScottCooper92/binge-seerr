package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListRowPoster
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.ui.state.ActionSheetGroup
import com.cooper.binge.companion.seerr.ui.state.actionItem
import com.cooper.binge.companion.seerr.ui.state.openItem
import com.binge.designsystem.R as DesR

/**
 * What the primary, or Manage, opens where no request of the viewer's is on file: the title's status and what more may be requested,
 * or a line saying why there is nothing to do. Watch is the page's own primary once the title is in the library, so it is not here.
 */
@Composable
fun TitleStatusSheet(
    state: TitleState,
    canRequest: Boolean,
    onDismiss: () -> Unit,
    onUnblock: (() -> Unit)? = null,
    fourKOpen: Boolean = false,
    remainingOpen: Boolean = false,
    onRequestFourK: () -> Unit = {},
    onRequestRemaining: () -> Unit = {},
    requestCount: Int = 0,
    onOpenRequests: () -> Unit = {},
    header: TitleSheetHeaderFacts? = null,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        TitleStatusSheetContent(
            state = state,
            canRequest = canRequest,
            onUnblock = onUnblock,
            fourKOpen = fourKOpen,
            remainingOpen = remainingOpen,
            onRequestFourK = onRequestFourK,
            onRequestRemaining = onRequestRemaining,
            requestCount = requestCount,
            onOpenRequests = {
                onDismiss()
                onOpenRequests()
            },
            header = header,
        )
    }
}

/** [TitleStatusSheet]'s body without its window, for a frame. */
@Composable
fun TitleStatusSheetContent(
    state: TitleState,
    canRequest: Boolean,
    modifier: Modifier = Modifier,
    onUnblock: (() -> Unit)? = null,
    fourKOpen: Boolean = false,
    remainingOpen: Boolean = false,
    onRequestFourK: () -> Unit = {},
    onRequestRemaining: () -> Unit = {},
    requestCount: Int = 0,
    onOpenRequests: () -> Unit = {},
    header: TitleSheetHeaderFacts? = null,
) {
    val requestRows =
        listOfNotNull(
            actionItem(Icons.Filled.Add, stringResource(R.string.title_sheet_request_remaining), onClick = onRequestRemaining)
                .takeIf { remainingOpen && state.isInLibrary() },
            actionItem(Icons.Filled.Add, stringResource(R.string.request_sheet_submit_4k), onClick = onRequestFourK)
                .takeIf { state.isInLibrary() && fourKOpen },
            openItem(
                Icons.AutoMirrored.Filled.ListAlt,
                stringResource(R.string.title_sheet_requests, requestCount),
                onClick = onOpenRequests,
            ).takeIf { requestCount > 1 },
        )
    if (requestRows.isNotEmpty()) {
        Column(modifier) {
            header?.let { TitleSheetHeader(it, stringResource(state.labelRes())) }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                ActionSheetGroup(
                    rows = requestRows,
                    title = stringResource(R.string.request_sheet_group_title),
                )
            }
        }
    } else {
        Column(
            modifier
                .padding(
                    horizontal = dimensionResource(DesR.dimen.padding_m),
                ).padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        ) {
            Text(stringResource(state.labelRes()), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(state.noteRes(canRequest)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state == TitleState.Blocklisted && onUnblock != null) {
                ItemGroup(
                    title = null,
                    rows = listOf(actionItem(Icons.Filled.LockOpen, stringResource(R.string.blocklist_unblock), onClick = onUnblock)),
                    modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_m)),
                )
            }
        }
    }
}

@StringRes
private fun TitleState.noteRes(canRequest: Boolean) =
    when (this) {
        TitleState.Request -> if (canRequest) R.string.title_sheet_note_nothing_to_request else R.string.title_sheet_note_cannot_request
        TitleState.Declined -> R.string.title_sheet_note_declined
        TitleState.Requested -> R.string.title_sheet_note_other_request
        TitleState.Processing -> R.string.title_sheet_note_processing
        TitleState.PartiallyAvailable, TitleState.Available -> R.string.title_sheet_note_in_library
        TitleState.Blocklisted -> R.string.title_sheet_note_blocklisted
    }

/** What a title's sheet says about the title itself above its rows. */
class TitleSheetHeaderFacts(
    val title: String,
    val posterUrl: String?,
)

/** The poster, the title and its combined state: what the sheet is about, as a request's sheet says it of the request. */
@Composable
fun TitleSheetHeader(
    facts: TitleSheetHeaderFacts,
    stateLabel: String,
) {
    Row(
        modifier =
            Modifier
                .padding(
                    horizontal = dimensionResource(DesR.dimen.padding_m),
                ).padding(bottom = dimensionResource(DesR.dimen.padding_m)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListRowPoster(imageUrl = facts.posterUrl, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(facts.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stateLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
