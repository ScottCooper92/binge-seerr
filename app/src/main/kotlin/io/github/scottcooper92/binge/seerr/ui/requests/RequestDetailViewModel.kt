package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.NoRequestStore
import io.github.scottcooper92.binge.seerr.data.RequestStore
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrCreateIssueBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.arrServer
import io.github.scottcooper92.binge.seerr.seerr.arrServers
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.details
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpCrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.ui.Ticker
import io.github.scottcooper92.binge.seerr.ui.minuteClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One request as a page. The request itself, its title's lookup, and the destination's names are
 * read together on open; the destination and the lookup are best-effort, so a title the server no
 * longer tracks or a service it has since removed still shows the request.
 *
 * [dispatcher] carries every network launch below, [moderation] and [editor] included, instead of
 * `viewModelScope`'s own `Dispatchers.Main.immediate` - see [IoDispatcher] and #177.
 */
@HiltViewModel(assistedFactory = RequestDetailViewModel.Factory::class)
class RequestDetailViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val requestId: Int,
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
        private val cache: RequestStore = NoRequestStore,
        private val minuteTicker: Ticker = Ticker(),
    ) : ViewModel() {
        private val state = MutableStateFlow<RequestDetailUiState>(RequestDetailUiState.Loading)

        internal var clock: () -> Long = System::currentTimeMillis

        /** The page's "2 minutes ago" is worded against this, moved on each minute while the page shows (#1239). */
        private val now = MutableStateFlow(clock())
        private var ticker: Job? = null

        /**
         * Raised when a moderation succeeds and held until the reload it triggers has written its result.
         * [RequestModeration] clears its own acting set before calling [onModerated], so without this the
         * reload is a second window with nothing marking it.
         */
        private val reloadingAfterAction = MutableStateFlow(false)

        /** A moderation reloads the page, so the chip and the history show the server's new answer. */
        private val moderator =
            RequestModeration(
                scope = viewModelScope,
                dispatcher = dispatcher,
                connection = connection,
                analytics = analytics,
                crashBreadcrumbs = crashBreadcrumbs,
                cache = cache,
                onModerated = ::reloadAfterAction,
            )

        /** The editor rides the page's state while it is open; it closes itself on the save landing. */
        private val requestEditor =
            RequestEditor(scope = viewModelScope, dispatcher = dispatcher, connection = connection, moderation = moderator)

        /**
         * What the screen may ask of the moderation and the editor: their actions only. Their flows are folded into
         * [uiState], and the moderation's events are [events] (#1048).
         */
        val moderation: RequestModerationControls = moderator
        val editor: RequestEditorControls = requestEditor

        /** How a moderation or an edit went, for the screen's snackbar. */
        val events: SharedFlow<ModerationEvent> = moderator.events

        val uiState: StateFlow<RequestDetailUiState> =
            combine(state, requestEditor.state, moderator.actingIds, reloadingAfterAction) { page, edit, acting, reloading ->
                (page as? RequestDetailUiState.Ready)?.copy(edit = edit, isActing = requestId in acting || reloading) ?: page
            }.combine(now) { page, now -> (page as? RequestDetailUiState.Ready)?.copy(now = now) ?: page }
                .stateIn(viewModelScope, SharingStarted.Lazily, RequestDetailUiState.Loading)

        private var editSource: EditSource? = null

        init {
            reload()
        }

        /**
         * The page's relative times count down while it shows, and no longer. A request's sheet opened over another
         * page never calls this: it is up for seconds, so the time it opened with stands.
         */
        fun setScreenVisible(visible: Boolean) {
            ticker?.cancel()
            ticker = null
            if (visible) ticker = viewModelScope.launch(dispatcher) { minuteClock(clock, minuteTicker).collect { now.value = it } }
        }

        fun startEdit() {
            val ready = state.value as? RequestDetailUiState.Ready ?: return
            if (ready.detail.canEdit) editSource?.let(requestEditor::start)
        }

        fun reload() {
            state.update { current ->
                when (current) {
                    is RequestDetailUiState.Ready -> current
                    is RequestDetailUiState.Seeded -> current.copy(error = null)
                    else -> RequestDetailUiState.Loading
                }
            }
            viewModelScope.launch(dispatcher) {
                seedFromCache()
                val result = attempt { load() }
                state.update { current ->
                    result.fold(
                        { RequestDetailUiState.Ready(it) },
                        { failure ->
                            // A seeded page keeps its header and reports the failure beside it.
                            (current as? RequestDetailUiState.Seeded)?.copy(error = failure.toSeerrError())
                                ?: RequestDetailUiState.Error(failure.toSeerrError())
                        },
                    )
                }
                reloadingAfterAction.value = false
            }
        }

        /** Shows the cached row's hero in place of the skeleton, if a list has the request and nothing has landed yet. */
        private suspend fun seedFromCache() {
            if (state.value !is RequestDetailUiState.Loading) return
            val item = attempt { cache.byId(requestId)?.toRequestItem() }.getOrNull() ?: return
            state.update { current -> if (current is RequestDetailUiState.Loading) RequestDetailUiState.Seeded(item) else current }
        }

        private fun reloadAfterAction() {
            reloadingAfterAction.value = true
            reload()
        }

        /** Files an issue against the request's media; the server keys issues on its own media id, not TMDB's. */
        fun reportIssue(
            type: IssueType,
            message: String,
        ) {
            val ready = state.value as? RequestDetailUiState.Ready ?: return
            val mediaId = ready.detail.mediaId ?: return
            if (ready.report == IssueReport.Sending) return
            state.value = ready.copy(report = IssueReport.Sending)
            crashBreadcrumbs.key("request_id", requestId.toString())
            crashBreadcrumbs.log("reporting issue on request")
            viewModelScope.launch(dispatcher) {
                val outcome =
                    attempt { connection.api().createIssue(SeerrCreateIssueBody(mediaId, type.code, message.trim())) }
                        .fold({ IssueReport.Sent }, { IssueReport.Failed(it.toSeerrError()) })
                if (outcome is IssueReport.Sent) analytics.event(AnalyticsEvents.ISSUE_REPORTED)
                state.update { current -> (current as? RequestDetailUiState.Ready)?.copy(report = outcome) ?: current }
            }
        }

        /** A dismiss while a send is in flight only hides the sheet; it must not clear [IssueReport.Sending], or reopening it loses [reportIssue]'s re-entrancy guard and lets a second POST fire. */
        fun dismissReport() =
            state.update { current ->
                val ready = current as? RequestDetailUiState.Ready ?: return@update current
                if (ready.report == IssueReport.Sending) ready else ready.copy(report = IssueReport.Idle)
            }

        private suspend fun load(): RequestDetail {
            val sources = fetchSources()
            editSource =
                EditSource(
                    request = sources.dto,
                    details = sources.details,
                    canEditDestination = sources.canEditDestination,
                    seasonsEditable = sources.seasonsEditable,
                )
            return sources.toDetail()
        }

        /**
         * Everything the page reads, fetched together. The request comes first because the rest is
         * keyed on what it names; the title lookup, the destination's names and the watch data are
         * best-effort, so a title the server no longer tracks still renders the request.
         */
        private suspend fun fetchSources(): DetailSources =
            coroutineScope {
                val api = connection.api()
                val profile = async { connection.profile() }
                val user = async { attempt { connection.authenticatedUser() }.getOrNull() }
                val permissions = async { user.await().toPermissions() }
                val dto = api.request(requestId)
                val details = async { attempt { api.details(dto.media.mediaType, dto.media.tmdbId) }.getOrNull() }
                val destination = async { dto.destination(api) }
                val watch =
                    async {
                        val mediaId = dto.media.id
                        if (mediaId != null && permissions.await().isAdmin && profile.await().hasWatchData) {
                            attempt { api.watchData(mediaId) }.getOrNull()
                        } else {
                            null
                        }
                    }
                DetailSources(
                    api = api,
                    dto = dto,
                    details = details.await(),
                    profile = profile.await(),
                    user = user.await(),
                    permissions = permissions.await(),
                    destination = destination.await(),
                    watch = watch.await(),
                    webRoot = connection.current().baseUrl,
                )
            }

        /** The service lists name the ids the request carries; a service the admin removed leaves the id unnamed. */
        private suspend fun SeerrRequestDto.destination(api: SeerrApi): RequestDestination? {
            val serverId = serverId ?: return null
            // Not `isTv`: this page reads anything that is not a film as a series, which is a
            // different answer from the picker's for a media type that is neither.
            val notMovie = media.mediaType != SEERR_MEDIA_TYPE_MOVIE
            val servers = attempt { api.arrServers(notMovie) }.getOrNull()
            val details = attempt { api.arrServer(notMovie, serverId) }.getOrNull()
            return RequestDestination(
                serverName = servers?.firstOrNull { it.id == serverId }?.name ?: details?.server?.name,
                profileName = details?.profiles?.firstOrNull { it.id == profileId }?.name,
                rootFolder = rootFolder,
                tags = tags.orEmpty().mapNotNull { id -> details?.tags?.firstOrNull { it.id == id }?.label },
            )
        }

        @AssistedFactory
        interface Factory {
            fun create(requestId: Int): RequestDetailViewModel
        }
    }
