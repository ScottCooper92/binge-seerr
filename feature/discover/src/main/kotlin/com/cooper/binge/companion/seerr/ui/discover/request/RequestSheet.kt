package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeSheetKind
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.ui.DestinationIcons
import com.cooper.binge.companion.seerr.ui.requests.DestinationPickerPanel
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
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
            key =
                "request-${target.kind}-${target.tmdbId}-${target.standardOpen}-${target.fourKOpen}" +
                    "-${target.seasonChoices.hashCode()}-${target.films.hashCode()}",
            creationCallback = { factory -> factory.create(target) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val links = LocalServerSettingsLinks.current
    val serviceType = if (target.kind == TitleKind.Series) ServiceType.Sonarr else ServiceType.Radarr
    var refreshServers by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (refreshServers) {
            refreshServers = false
            viewModel.retry(RequestRead.Servers)
        }
    }
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
            target = target,
            actions =
                RequestSheetActions(
                    onUse4k = viewModel::setUse4k,
                    onSeason = viewModel::toggleSeasons,
                    onAllSeasons = { viewModel.toggleSeasons(null) },
                    onFilm = viewModel::toggleFilms,
                    onAllFilms = { viewModel.toggleFilms(null) },
                    onServer = viewModel::selectServer,
                    onProfile = viewModel::selectProfile,
                    onRootFolder = viewModel::selectRootFolder,
                    onToggleTag = viewModel::toggleTag,
                    onRequestFor = viewModel::selectRequestFor,
                    onSubmit = viewModel::submit,
                    onRetry = viewModel::load,
                    onRetryRead = viewModel::retry,
                    onDismissProblem = viewModel::dismissProblem,
                    onOpenFailure = viewModel::openFailure,
                    onDismissFailure = viewModel::dismissFailure,
                    // The sheet stays open under the new server's page, and reads the servers again on the way back.
                    onAddServer =
                        links?.let { open ->
                            {
                                refreshServers = true
                                open.onAdd(serviceType)
                            }
                        },
                ),
        )
    }
}

class RequestSheetActions(
    val onUse4k: (Boolean) -> Unit,
    val onSeason: (Int) -> Unit,
    val onAllSeasons: () -> Unit,
    val onFilm: (Int) -> Unit,
    val onAllFilms: () -> Unit,
    val onServer: (Int) -> Unit,
    val onProfile: (Int) -> Unit,
    val onRootFolder: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryRead: (RequestRead) -> Unit,
    val onDismissProblem: () -> Unit,
    val onOpenFailure: () -> Unit,
    val onDismissFailure: () -> Unit,
    val onToggleTag: (Int) -> Unit = {},
    val onRequestFor: (Int) -> Unit = {},
    /** Add a server of this kind; null where the sheet has no host to open its page. */
    val onAddServer: (() -> Unit)? = null,
)

/** How many other users make the "Request for" page worth a search field; a handful reads at a glance. */
private const val USER_SEARCH_FROM = 8

/** A page of the sheet: the form, or one of the lists it opens. */
private enum class SheetPage { Form, Seasons, Films, Server, Profile, RootFolder, Tags, RequestFor }

@Composable
fun RequestSheetContent(
    state: RequestSheetUiState,
    actions: RequestSheetActions,
    modifier: Modifier = Modifier,
    target: RequestSheetTarget? = null,
) {
    when (state) {
        RequestSheetUiState.Loading -> if (target != null) LoadingForm(target, modifier) else LoadingScreen(modifier)
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
    var page by rememberSaveable { mutableStateOf(SheetPage.Form) }
    if (page != SheetPage.Form) {
        BackHandler(onBack = { page = SheetPage.Form })
        PageFor(page, state, actions) { page = SheetPage.Form }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(
                        1f,
                        fill = false,
                    ).verticalScroll(rememberScrollState())
                    .padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            RequestHeader(state.target, quota = quotaLine(state))
            RequestOptionsGroup(
                state,
                actions,
                onOpenSeasons = { page = SheetPage.Seasons },
                onOpenFilms = { page = SheetPage.Films },
                onOpenDestination = { page = it.toPage() },
            )
        }
        RequestFooter(state, actions)
    }
    val resources = LocalContext.current.resources
    MessageDialogHost(message = state.problem?.toDialogMessage(resources::getString), onDismiss = actions.onDismissProblem)
    state.failure?.takeIf { state.failureDialog }?.let { failure ->
        val message = failure.toDialogMessage(resources::getString)
        BingeConfirmDialog(
            title = message.title,
            message = message.body,
            confirmLabel = stringResource(R.string.action_try_again),
            onConfirm = {
                actions.onDismissFailure()
                actions.onSubmit()
            },
            onDismissRequest = actions.onDismissFailure,
        )
    }
}

/**
 * The sheet before the user's access is known: the title's header and the Request button, disabled, so the sheet opens at the
 * shape it keeps and only the options grow in between them.
 */
@Composable
private fun LoadingForm(
    target: RequestSheetTarget,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        RequestHeader(target, modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)))
        BingeActionFooter(
            label = stringResource(R.string.request_sheet_submit),
            onClick = {},
            enabled = false,
            loading = false,
            failed = false,
        )
    }
}

private fun DestinationField.toPage() =
    when (this) {
        DestinationField.Server -> SheetPage.Server
        DestinationField.Profile -> SheetPage.Profile
        DestinationField.RootFolder -> SheetPage.RootFolder
        DestinationField.Tags -> SheetPage.Tags
        DestinationField.RequestFor -> SheetPage.RequestFor
    }

/** The pinned button. It reads "Request in 4K" for 4K, and turns red with a short reason after a send that got no answer. */
@Composable
private fun RequestFooter(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
) {
    val failed = state.footerFailed && state.failure != null
    val label =
        when {
            failed && state.failure?.cause == RequestProblemCause.Unreachable -> stringResource(R.string.request_sheet_failed_unreachable)
            failed -> stringResource(R.string.request_sheet_failed_other)
            state.films != null -> filmsSubmitLabel(state.films?.requestIds?.size ?: 0)
            state.use4k -> stringResource(R.string.request_sheet_submit_4k)
            else -> stringResource(R.string.request_sheet_submit)
        }
    BingeActionFooter(
        label = label,
        onClick = if (failed) actions.onOpenFailure else actions.onSubmit,
        enabled = state.canSubmit || failed,
        loading = state.submitting,
        failed = failed,
    )
}

@Composable
private fun PageFor(
    page: SheetPage,
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
    onBack: () -> Unit,
) {
    val pick = state.pick
    when (page) {
        SheetPage.Seasons -> state.seasons?.let { RequestSeasonPage(it, !state.submitting, actions, onBack) }
        SheetPage.Films -> state.films?.let { RequestFilmsPage(state.target.films, it, state.use4k, !state.submitting, actions, onBack) }
        SheetPage.Server ->
            pick?.let {
                DestinationPickerPanel(
                    icon = DestinationIcons.server,
                    title = stringResource(R.string.advanced_server),
                    choices = state.servers4k.map { it.id to it.name },
                    selected = it.serverId,
                    onSelect = { id ->
                        actions.onServer(id)
                        onBack()
                    },
                    onBack = onBack,
                    footer = { ServerPageFooter(state, actions) },
                )
            }
        SheetPage.Profile ->
            (pick?.choices as? ChoicesState.Ready)?.let { ready ->
                DestinationPickerPanel(
                    icon = DestinationIcons.profile,
                    title = stringResource(R.string.advanced_profile),
                    choices = ready.choices.profiles.map { it.id to ready.choices.profileLabel(it, pick.anime) },
                    selected = pick.profileId,
                    onSelect = { id ->
                        actions.onProfile(id)
                        onBack()
                    },
                    onBack = onBack,
                )
            }
        SheetPage.RootFolder ->
            (pick?.choices as? ChoicesState.Ready)?.let { ready ->
                DestinationPickerPanel(
                    icon = DestinationIcons.rootFolder,
                    title = stringResource(R.string.advanced_root_folder),
                    choices = ready.choices.rootFolders.map { it to ready.choices.folderLabel(it) },
                    selected = pick.rootFolder,
                    onSelect = { path ->
                        actions.onRootFolder(path)
                        onBack()
                    },
                    onBack = onBack,
                )
            }
        SheetPage.Tags ->
            (pick?.choices as? ChoicesState.Ready)?.let { ready ->
                RequestTagsPage(ready.choices.tags, pick.shownTagIds(), !state.submitting, actions.onToggleTag, onBack)
            }
        SheetPage.RequestFor ->
            (state.users as? UsersState.Ready)?.let { ready ->
                val me = state.access.viewerId
                val others = ready.users.filter { it.id != me }.map { it.id to it.name }
                DestinationPickerPanel(
                    icon = DestinationIcons.user,
                    title = stringResource(R.string.request_for),
                    choices = listOf(me to stringResource(R.string.request_for_me)) + others,
                    selected = state.requestFor?.id ?: me,
                    onSelect = { id ->
                        actions.onRequestFor(id)
                        onBack()
                    },
                    onBack = onBack,
                    filterPlaceholder = stringResource(R.string.request_for_search).takeIf { others.size >= USER_SEARCH_FROM },
                    noMatchLabel = stringResource(R.string.request_for_no_match),
                )
            }
        SheetPage.Form -> Unit
    }
}

/** The dialog for a refused send: what happened, then the cause and the next step, then the server's own words if it gave any. */
fun RequestProblem.toDialogMessage(string: (Int) -> String): DialogMessage {
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
        RequestProblemCause.Other -> R.string.request_problem_other
    }

/** The snackbar sentence for a request that went through, for the host to show once it has closed the sheet. */
fun RequestResult.messageRes(): Int =
    when (this) {
        RequestResult.Created -> R.string.request_sheet_done_created
        RequestResult.AlreadyRequested -> R.string.request_sheet_done_already
        RequestResult.NothingToRequest -> R.string.request_sheet_done_nothing
        RequestResult.PartiallyCreated -> R.string.request_sheet_done_partial
    }
