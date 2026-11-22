package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRanges
import com.binge.designsystem.formatRelativeOrAbsolute
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.state.RequestStateChip
import com.binge.designsystem.R as DesR

/**
 * The requests on a title as the title level draws them: [summaries] to show, [ownId] the request the sheet's own view model
 * serves (its events are already handled), and what a card's tap and a decision on it call back.
 */
internal class RequestCardsModel(
    val summaries: List<RequestSummary>,
    val ownId: Int,
    val onOpen: (Int) -> Unit,
    val snackbar: SnackbarHostState,
    val onChanged: () -> Unit,
)

/** Pending first, then approved or processing, then completed, then declined or failed; newest first within each. */
internal fun List<RequestSummary>.inCardOrder(): List<RequestSummary> =
    sortedWith(
        compareBy<RequestSummary> {
            it.status.cardRank()
        }.thenByDescending { it.id },
    )

private fun SeerrRequestStatusCode?.cardRank(): Int =
    when (this) {
        SeerrRequestStatusCode.Pending -> 0
        SeerrRequestStatusCode.Completed -> 2
        SeerrRequestStatusCode.Declined, SeerrRequestStatusCode.Failed -> 3
        else -> 1
    }

/** Only a pending request waits on a decision. */
internal fun RequestSummary.offersDecision(): Boolean = status == SeerrRequestStatusCode.Pending

/** The Requests section: one compact card per request, each opening its own level, a pending one with its decision beneath. */
@Composable
internal fun RequestCardsSection(cards: RequestCardsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        cards.summaries.inCardOrder().forEachIndexed { index, request ->
            ItemGroup(
                title = stringResource(R.string.title_sheet_group_requests).takeIf { index == 0 },
                rows = listOf(requestCardRow(request) { cards.onOpen(request.id) }),
            )
            if (request.offersDecision()) CardDecision(request.id, cards)
        }
    }
}

@Composable
private fun requestCardRow(
    request: RequestSummary,
    onClick: () -> Unit,
): ListItem {
    val name = request.requestedBy ?: stringResource(R.string.requests_requester_unknown)
    return ListItem(
        icon = Icons.Filled.Person,
        leadingContent = {
            BingeInitialsAvatar(name = name, avatarUrl = request.requestedByAvatarUrl, size = dimensionResource(DesR.dimen.avatar_size_md))
        },
        label = name,
        detail =
            listOfNotNull(
                formatRelativeOrAbsolute(request.requestedAtMillis),
                request.seasonNumbers.takeIf { it.isNotEmpty() }?.let {
                    pluralStringResource(R.plurals.requests_seasons, it.size, it.formatRanges())
                },
            ).joinToString(stringResource(R.string.hub_meta_separator)).ifBlank { null },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (request.is4k) BingeTag(label = stringResource(R.string.settings_service_4k))
                request.status?.let { RequestStateChip(status = it) }
            }
        },
        onClick = onClick,
    )
}

/** Approve and Decline for a pending request this viewer may moderate, through that request's own view model; nothing otherwise. */
@Composable
private fun CardDecision(
    id: Int,
    cards: RequestCardsModel,
) {
    val viewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(
            key = "request-detail-$id",
            creationCallback = { factory -> factory.create(id) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (id != cards.ownId) {
        ModerationSnackbarEffect(viewModel.events, cards.snackbar, viewModel.moderation::blockTitle, onNotNow = {})
        LaunchedEffect(viewModel) { viewModel.events.collect { cards.onChanged() } }
    }
    val detail = (state as? RequestDetailUiState.Ready)?.detail ?: return
    CardDecisionBar(
        actions = detail.actions,
        onApprove = { viewModel.moderation.approve(id) },
        onDecline = { viewModel.moderation.decline(detail.item, false) },
    )
}

/** Approve beside Decline, where this viewer may approve at all; nothing for one who may not. */
@Composable
internal fun CardDecisionBar(
    actions: RequestActions,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    if (!actions.canApprove) return
    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        if (actions.canDecline) {
            BingeOutlinedButton(
                label = stringResource(R.string.request_decline),
                onClick = onDecline,
                destructive = true,
                modifier = Modifier.weight(1f),
            )
        }
        BingeFilledButton(
            label = stringResource(R.string.request_approve),
            onClick = onApprove,
            modifier = Modifier.weight(1f),
        )
    }
}
