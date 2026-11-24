package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRanges
import com.binge.designsystem.formatRelativeOrAbsolute
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.state.RequestStateChip
import com.binge.designsystem.R as DesR
import com.cooper.binge.companion.seerr.core.ui.R as UiR

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

private const val RANK_PENDING = 0
private const val RANK_OPEN = 1
private const val RANK_COMPLETED = 2
private const val RANK_ENDED = 3

private fun SeerrRequestStatusCode?.cardRank(): Int =
    when (this) {
        SeerrRequestStatusCode.Pending -> RANK_PENDING
        SeerrRequestStatusCode.Completed -> RANK_COMPLETED
        SeerrRequestStatusCode.Declined, SeerrRequestStatusCode.Failed -> RANK_ENDED
        else -> RANK_OPEN
    }

/** Only a pending request waits on a decision. */
internal fun RequestSummary.offersDecision(): Boolean = status == SeerrRequestStatusCode.Pending

/** The Requests section: one compact card per request, each opening its own level, a pending one with its decision inside it. */
@Composable
internal fun RequestCardsSection(cards: RequestCardsModel) {
    // The order the sheet opened with holds while it is open, so a card decided does not jump; a new one goes last.
    val opened = remember { cards.summaries.inCardOrder().map { it.id } }
    val shown = cards.summaries.sortedBy { opened.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        shown.forEachIndexed { index, request ->
            ItemGroup(
                title = stringResource(R.string.title_sheet_group_requests).takeIf { index == 0 },
                rows = listOf(requestCardRow(request) { cards.onOpen(request.id) }),
                belowRows = if (request.offersDecision()) ({ CardDecision(request.id, cards) }) else null,
            )
        }
    }
}

@Composable
private fun requestCardRow(
    request: RequestSummary,
    onClick: () -> Unit,
): ListItem {
    val name = request.requestedBy ?: stringResource(UiR.string.requests_requester_unknown)
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
                    pluralStringResource(UiR.plurals.requests_seasons, it.size, it.formatRanges())
                },
            ).joinToString(stringResource(UiR.string.hub_meta_separator)).ifBlank { null },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (request.is4k) BingeTag(label = stringResource(UiR.string.settings_service_4k))
                request.status?.let { RequestStateChip(status = it) }
            }
        },
        onClick = onClick,
    )
}

/**
 * Approve and Decline for a pending request this viewer may moderate, through that request's own view model; nothing otherwise.
 * Each button spins while its call runs, Decline confirms with the block switch first, and a decision that lands takes the
 * buttons away in place. The sheet stays open whichever card was decided.
 */
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
    var busy by remember { mutableStateOf<CardBusy?>(null) }
    var decided by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var block by rememberSaveable { mutableStateOf(false) }
    if (id != cards.ownId) {
        ModerationSnackbarEffect(viewModel.events, cards.snackbar, viewModel.moderation::blockTitle, onNotNow = {})
        LaunchedEffect(viewModel) { viewModel.events.collect { cards.onChanged() } }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (busy != null && !event.isError()) decided = true
            busy = null
        }
    }
    val detail = (state as? RequestDetailUiState.Ready)?.detail ?: return
    if (decided) return
    CardDecisionBar(
        actions = detail.actions,
        onApprove = {
            busy = CardBusy.Approve
            viewModel.moderation.approve(id)
        },
        onDecline = { confirming = true },
        approving = busy == CardBusy.Approve,
        declining = busy == CardBusy.Decline,
    )
    if (confirming) {
        RequestConfirm(
            removing = false,
            removeMessage = "",
            onConfirm = {
                busy = CardBusy.Decline
                viewModel.moderation.decline(detail.item, block)
            },
            onStep = {
                confirming = false
                block = false
            },
            blockTitle = block,
            onBlockTitleChange = if (detail.actions.canBlock) ({ block = it }) else null,
        )
    }
}

private enum class CardBusy { Approve, Decline }

/** Approve beside Decline, where this viewer may approve at all; nothing for one who may not. Either spins while it runs. */
@Composable
internal fun CardDecisionBar(
    actions: RequestActions,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    approving: Boolean = false,
    declining: Boolean = false,
) {
    if (!actions.canApprove) return
    val running = approving || declining
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_s))
                .padding(bottom = dimensionResource(DesR.dimen.padding_xs)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        if (actions.canDecline) {
            BingeTextButton(
                label = stringResource(R.string.request_decline),
                onClick = onDecline,
                destructive = true,
                loading = declining,
                enabled = !running,
                modifier = Modifier.weight(1f),
            )
        }
        BingeTextButton(
            label = stringResource(R.string.request_approve),
            onClick = onApprove,
            loading = approving,
            enabled = !running,
            modifier = Modifier.weight(1f),
        )
    }
}
