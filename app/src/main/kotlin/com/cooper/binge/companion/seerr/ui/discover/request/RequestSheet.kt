package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.SwitchRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.ui.ChoiceField
import com.cooper.binge.companion.seerr.ui.DestinationIcons
import com.cooper.binge.companion.seerr.ui.requests.DestinationPickerPanel
import com.cooper.binge.companion.seerr.ui.state.DialogMessage
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.MessageDialogHost
import com.binge.designsystem.R as DesR

/**
 * The request sheet: the one way a title is requested in the app. It offers 4K where this user may ask for it, the
 * server, profile and folder where they may choose a destination, and the quota the request would use. A refusal
 * from the server is a dialog the user acknowledges, because its cause and next step must be read.
 *
 * [onResult] is called once the server has answered a request, and the host closes the sheet and says how it went.
 */
@Composable
fun RequestSheet(
    target: RequestSheetTarget,
    onDismiss: () -> Unit,
    onResult: (RequestResult) -> Unit,
) {
    val viewModel =
        hiltViewModel<RequestSheetViewModel, RequestSheetViewModel.Factory>(
            key = "request-${target.kind}-${target.tmdbId}-${target.standardOpen}-${target.fourKOpen}",
            creationCallback = { factory -> factory.create(target) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RequestSheetEvent.Done -> onResult(event.result)
            }
        }
    }
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) {
        RequestSheetContent(
            state = state,
            actions =
                RequestSheetActions(
                    onUse4k = viewModel::setUse4k,
                    onServer = viewModel::selectServer,
                    onProfile = viewModel::selectProfile,
                    onRootFolder = viewModel::selectRootFolder,
                    onSubmit = viewModel::submit,
                    onRetry = viewModel::load,
                    onDismissProblem = viewModel::dismissProblem,
                ),
        )
    }
}

class RequestSheetActions(
    val onUse4k: (Boolean) -> Unit,
    val onServer: (Int) -> Unit,
    val onProfile: (Int) -> Unit,
    val onRootFolder: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onRetry: () -> Unit,
    val onDismissProblem: () -> Unit,
)

/** Which destination field's picker is swapped in for the form; see [DestinationPickerPanel]. */
private enum class PickerField { Server, Profile, RootFolder }

@Composable
internal fun RequestSheetContent(
    state: RequestSheetUiState,
    actions: RequestSheetActions,
    modifier: Modifier = Modifier,
) {
    when (state) {
        RequestSheetUiState.Loading -> LoadingScreen(modifier)
        is RequestSheetUiState.Error -> ErrorScreen(error = state.error, modifier = modifier, onRetry = actions.onRetry)
        is RequestSheetUiState.Ready -> ReadyContent(state, actions, modifier)
    }
}

@Composable
private fun ReadyContent(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
    modifier: Modifier,
) {
    var picker by rememberSaveable { mutableStateOf<PickerField?>(null) }
    val pick = state.pick
    if (picker != null && pick != null) {
        BackHandler(onBack = { picker = null })
        PickerFor(picker, state, actions) { picker = null }
        return
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Text(stringResource(R.string.request_sheet_title), style = MaterialTheme.typography.titleMedium)
        Text(state.target.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            if (state.options.may4k && state.target.fourKOpen) FourKRow(state, actions)
            if (pick != null) DestinationFields(state, pick) { picker = it }
        }
        state.options.quota?.let { QuotaRow(it) }
        BingeActionFooter(
            label = stringResource(R.string.request_sheet_submit),
            onClick = actions.onSubmit,
            enabled = state.canSubmit,
            loading = state.submitting,
        )
    }
    val resources = LocalContext.current.resources
    MessageDialogHost(
        message = state.problem?.toDialogMessage(resources::getString),
        onDismiss = actions.onDismissProblem,
    )
}

@Composable
private fun FourKRow(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Icon(Icons.Filled.Hd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchRow(
            label = stringResource(R.string.request_sheet_4k),
            checked = state.use4k,
            onCheckedChange = actions.onUse4k,
            enabled = state.canToggle4k,
        )
    }
}

@Composable
private fun DestinationFields(
    state: RequestSheetUiState.Ready,
    pick: DestinationPick,
    onOpen: (PickerField) -> Unit,
) {
    val enabled = !state.submitting
    ChoiceField(
        icon = DestinationIcons.server,
        title = stringResource(R.string.advanced_server),
        choices = state.servers.map { it.id to it.name },
        selected = pick.serverId,
        enabled = enabled,
    ) { onOpen(PickerField.Server) }
    pick.choices?.let { choices ->
        ChoiceField(
            icon = DestinationIcons.profile,
            title = stringResource(R.string.advanced_profile),
            choices = choices.profiles.map { it.id to it.label },
            selected = pick.profileId,
            enabled = enabled,
        ) { onOpen(PickerField.Profile) }
        ChoiceField(
            icon = DestinationIcons.rootFolder,
            title = stringResource(R.string.advanced_root_folder),
            choices = choices.rootFolders.map { it to it },
            selected = pick.rootFolder,
            enabled = enabled,
        ) { onOpen(PickerField.RootFolder) }
    }
}

@Composable
private fun PickerFor(
    field: PickerField?,
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
    onBack: () -> Unit,
) {
    val pick = state.pick ?: return
    when (field) {
        PickerField.Server ->
            DestinationPickerPanel(
                icon = DestinationIcons.server,
                title = stringResource(R.string.advanced_server),
                choices = state.servers.map { it.id to it.name },
                selected = pick.serverId,
                onSelect = {
                    actions.onServer(it)
                    onBack()
                },
                onBack = onBack,
            )
        PickerField.Profile ->
            DestinationPickerPanel(
                icon = DestinationIcons.profile,
                title = stringResource(R.string.advanced_profile),
                choices =
                    pick.choices
                        ?.profiles
                        .orEmpty()
                        .map { it.id to it.label },
                selected = pick.profileId,
                onSelect = {
                    actions.onProfile(it)
                    onBack()
                },
                onBack = onBack,
            )
        PickerField.RootFolder ->
            DestinationPickerPanel(
                icon = DestinationIcons.rootFolder,
                title = stringResource(R.string.advanced_root_folder),
                choices =
                    pick.choices
                        ?.rootFolders
                        .orEmpty()
                        .map { it to it },
                selected = pick.rootFolder,
                onSelect = {
                    actions.onRootFolder(it)
                    onBack()
                },
                onBack = onBack,
            )
        null -> Unit
    }
}

/** How much of the quota is left, in the words the hub's quota card uses; a spent quota says what that means for this request. */
@Composable
private fun QuotaRow(quota: RequestQuota) {
    val left = pluralStringResource(R.plurals.hub_quota_remaining, quota.remaining, quota.remaining, quota.limit)
    val period = quota.days?.let { pluralStringResource(R.plurals.hub_quota_period, it, it) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(
                text = listOfNotNull(left, period).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (quota.spent) {
                Text(
                    text = stringResource(R.string.request_sheet_quota_spent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The dialog for a refused send: what happened, then the cause and the next step, then the server's own words if it gave any. */
internal fun RequestProblem.toDialogMessage(string: (Int) -> String): DialogMessage {
    val body = string(cause.bodyRes())
    return DialogMessage(
        title = string(R.string.request_problem_title),
        body = detail?.let { "$body\n\n$it" } ?: body,
    )
}

private fun RequestProblemCause.bodyRes(): Int =
    when (this) {
        RequestProblemCause.Quota -> R.string.request_problem_quota
        RequestProblemCause.NotAllowed -> R.string.request_problem_not_allowed
        RequestProblemCause.Unreachable -> R.string.request_problem_unreachable
        RequestProblemCause.AlreadyThere -> R.string.request_problem_already_there
        RequestProblemCause.Other -> R.string.request_problem_other
    }

/** The snackbar sentence for a request that went through, for the host to show once it has closed the sheet. */
fun RequestResult.messageRes(): Int =
    when (this) {
        RequestResult.Created -> R.string.request_sheet_done_created
        RequestResult.AlreadyRequested -> R.string.request_sheet_done_already
        RequestResult.NothingToRequest -> R.string.request_sheet_done_nothing
    }
