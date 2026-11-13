package com.cooper.binge.companion.seerr.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.binge.designsystem.refresh.RefreshTracker
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.NoRequestStore
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.review.NoReviewSignals
import com.cooper.binge.companion.seerr.review.ReviewSignalSink
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrCreateIssueBody
import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.arrServer
import com.cooper.binge.companion.seerr.seerr.arrServers
import com.cooper.binge.companion.seerr.seerr.details
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.CrashBreadcrumbs
import com.cooper.binge.companion.seerr.telemetry.NoOpAnalytics
import com.cooper.binge.companion.seerr.telemetry.NoOpCrashBreadcrumbs
import com.cooper.binge.companion.seerr.ui.Ticker
import com.cooper.binge.companion.seerr.ui.minuteClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
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
        private val queueGates: DownloadQueueGates = DownloadQueueGates(connection),
        private val reviewSignals: ReviewSignalSink = NoReviewSignals,
    ) : ViewModel() {
        private val state = MutableStateFlow<RequestDetailUiState>(RequestDetailUiState.Loading)

        internal var clock: () -> Long = System::currentTimeMillis

        /** The page's "2 minutes ago" is worded against this, moved on each minute while the page shows (#1239). */
        private val now = MutableStateFlow(clock())
        private var ticker: Job? = null
        private val refreshTracker = RefreshTracker()

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
                reviewSignals = reviewSignals,
                onModerated = ::reloadAfterAction,
            )

        /**
         * How deleting each instance's files went, by `is4k`. Jellyseerr 2.x leaves the status as it was afterwards, and a
         * refusal changes nothing on the server, so the reload alone would show neither; this marks the instance until the page goes.
         */
        private val filesDeleted = MutableStateFlow<Map<Boolean, FilesDeletion>>(emptyMap())

        /** Clearing the title's data is running; the page does not close until the server has answered. */
        private val clearing = MutableStateFlow(false)

        /** The Decline or Remove that is running or failed; the sheet stays open and its control shows it. */
        private val track = MutableStateFlow(ActionTrack())

        /** The editor rides the page's state while it is open; it closes itself on the save landing. */
        private val requestEditor =
            RequestEditor(scope = viewModelScope, dispatcher = dispatcher, connection = connection, moderation = moderator)

        /**
         * What the screen may ask of the moderation and the editor: their actions only. Their flows are folded into
         * [uiState], and the moderation's events are [events] (#1048).
         */
        val moderation: RequestModerationControls =
            object : RequestModerationControls by moderator {
                override fun deleteMediaFiles(
                    requestId: Int,
                    mediaId: Int,
                    is4k: Boolean,
                    removeRequestIds: List<Int>,
                ) {
                    val before = filesDeleted.value[is4k]
                    filesDeleted.update { it + (is4k to FilesDeletion.Running) }
                    val started =
                        moderator.deleteMediaFiles(requestId, mediaId, is4k, removeRequestIds) { outcome ->
                            filesDeleted.update { it + (is4k to outcome) }
                        }
                    // Another action on the request is running, so nothing was sent: the row is as it was.
                    if (!started) filesDeleted.update { if (before == null) it - is4k else it + (is4k to before) }
                }

                override fun clearMedia(
                    requestId: Int,
                    mediaId: Int,
                ) {
                    clearing.value = true
                    val started = moderator.clearMedia(requestId, mediaId) { succeeded -> if (!succeeded) clearing.value = false }
                    if (!started) clearing.value = false
                }

                override fun setMediaStatus(
                    requestId: Int,
                    mediaId: Int,
                    status: MediaStatusChoice,
                    is4k: Boolean,
                    seasonNumbers: List<Int>,
                ) = tracked(RequestAction.MarkAs, is4k) { settled ->
                    moderator.setMediaStatus(requestId, mediaId, status, is4k, seasonNumbers, settled)
                }

                override fun decline(
                    item: RequestItem,
                    blockTitle: Boolean,
                ) = tracked(RequestAction.Decline) { settled -> moderator.decline(item, blockTitle, settled) }

                override fun remove(
                    item: RequestItem,
                    blockTitle: Boolean,
                ) = tracked(RequestAction.Remove) { settled -> moderator.remove(item, blockTitle, settled) }

                /** The sheet closes when [ActionTrack.landed] grows, which Decline and Remove do; a failure keeps it open on its control. */
                private fun tracked(
                    action: RequestAction,
                    is4k: Boolean = false,
                    start: (settled: (Boolean) -> Unit) -> Boolean,
                ) {
                    val before = track.value
                    val attempt = before.attempts + 1
                    track.value = before.copy(progress = ActionProgress(action, attempt = attempt, is4k = is4k), attempts = attempt)
                    val closes = if (action.closesSheet) 1 else 0
                    val started =
                        start { succeeded ->
                            track.update {
                                if (succeeded) {
                                    it.copy(progress = null, landed = it.landed + closes)
                                } else {
                                    it.copy(progress = ActionProgress(action, failed = true, attempt = attempt, is4k = is4k))
                                }
                            }
                        }
                    // Another action on the request is running, so nothing was sent: the control is as it was.
                    if (!started) track.update { it.copy(progress = before.progress, attempts = before.attempts) }
                }
            }
        val editor: RequestEditorControls = requestEditor

        /** How a moderation or an edit went, for the screen's snackbar. */
        val events: SharedFlow<ModerationEvent> = moderator.events

        val uiState: StateFlow<RequestDetailUiState> =
            combine(
                state,
                requestEditor.state,
                moderator.actingIds,
                reloadingAfterAction,
                filesDeleted,
            ) { page, edit, acting, reloading, deleted ->
                (page as? RequestDetailUiState.Ready)
                    ?.copy(
                        detail = page.detail.withFilesDeleted(deleted),
                        edit = edit,
                        isActing = requestId in acting || reloading,
                    )
                    ?: page
            }.combine(clearing) { page, clearingData ->
                (page as? RequestDetailUiState.Ready)?.copy(detail = page.detail.withClearing(clearingData)) ?: page
            }.combine(track) { page, ongoing ->
                (page as? RequestDetailUiState.Ready)?.copy(progress = ongoing.progress, actionsLanded = ongoing.landed) ?: page
            }.combine(now) { page, now -> (page as? RequestDetailUiState.Ready)?.copy(now = now) ?: page }
                .combine(refreshTracker.isRefreshing) { page, refreshing ->
                    (page as? RequestDetailUiState.Ready)?.copy(isRefreshing = refreshing) ?: page
                }.stateIn(viewModelScope, SharingStarted.Lazily, RequestDetailUiState.Loading)

        /** [ActionTrack.landed] as it stands, for a sheet that opens over a page that is not `Ready`. */
        fun actionsLandedNow(): Int = track.value.landed

        private var editSource: EditSource? = null

        /** The `auth/me` answer the page was last built from; null when that read failed. */
        @Volatile private var loadedUser: SeerrUserDto? = null

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

        /**
         * A Manage sheet opened: re-read `auth/me` if the cached copy is over a minute old, and rebuild the page if the
         * permissions moved, so the sheet offers what the server now allows (#1722). A page still loading reads it itself.
         */
        fun onManageOpened() {
            if (state.value !is RequestDetailUiState.Ready) return
            viewModelScope.launch(dispatcher) {
                val user = attempt { connection.recentAuthenticatedUser() }.getOrNull() ?: return@launch
                val loaded = loadedUser
                if (loaded == null || user.permissions != loaded.permissions) reload()
            }
        }

        /**
         * Forgets that deleting files was refused for want of a default server, because the admin is on their way to set
         * one (#1736). The row is then Delete files again when they are back; if they set nothing, the next try is refused again.
         */
        fun forgetNoDefaultServer() {
            filesDeleted.update { marks -> marks.filterValues { it != FilesDeletion.NoDefaultServer } }
        }

        fun startEdit() {
            val ready = state.value as? RequestDetailUiState.Ready ?: return
            if (ready.detail.canEdit) editSource?.let(requestEditor::start)
        }

        /**
         * A pull: re-reads the page and swaps it in, keeping what is showing until then. A failed read leaves the page as it
         * is and says so in the usual message. [reload] does the same for a page already showing; it differs on a first load
         * or a seeded page, which it replaces with the error.
         */
        fun refresh() {
            if (state.value !is RequestDetailUiState.Ready) return
            viewModelScope.launch(dispatcher) {
                attempt { refreshTracker.track { load() } }.fold(
                    { detail ->
                        // The page's own fields stay: a report still being sent keeps its re-entrancy guard.
                        state.update { current -> (current as? RequestDetailUiState.Ready)?.copy(detail = detail) ?: current }
                    },
                    { failure -> moderator.announce(ModerationEvent.Failed(failure.toSeerrError())) },
                )
            }
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
                        // A page already showing keeps its own fields: a report still being sent keeps its re-entrancy guard.
                        { detail -> (current as? RequestDetailUiState.Ready)?.copy(detail = detail) ?: RequestDetailUiState.Ready(detail) },
                        { failure ->
                            // A page already showing stays as it is; a seeded one keeps its header and reports the failure beside it.
                            current as? RequestDetailUiState.Ready
                                ?: (current as? RequestDetailUiState.Seeded)?.copy(error = failure.toSeerrError())
                                ?: RequestDetailUiState.Error(failure.toSeerrError())
                        },
                    )
                }
                // The page that stayed says so in the usual message, where a first load shows the error screen. After a
                // moderation the action's own event is the message, so a failed follow-up read stays quiet.
                val afterAction = reloadingAfterAction.value
                result.exceptionOrNull()?.let { failure ->
                    if (!afterAction && state.value is RequestDetailUiState.Ready) {
                        moderator.announce(ModerationEvent.Failed(failure.toSeerrError()))
                    }
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
            loadedUser = sources.user
            editSource =
                EditSource(
                    request = sources.dto,
                    details = sources.details,
                    canEditDestination = sources.canEditDestination,
                    seasonsEditable = sources.seasonsEditable,
                    queueGate = sources.queueGate,
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
                val user = async { attempt { connection.recentAuthenticatedUser() }.getOrNull() }
                val permissions = async { user.await().toPermissions() }
                val dto = api.request(requestId)
                val details = async { attempt { api.details(dto.media.mediaType, dto.media.tmdbId) }.getOrNull() }
                val destination = async { dto.destination(api) }
                val queueGate = async { queueGates.current() }
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
                    queueGate = queueGate.await(),
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

/** Marks the title's data as being cleared, so its row shows the progress until the page closes or the clear fails. */
private fun RequestDetail.withClearing(clearing: Boolean): RequestDetail =
    if (clearing) copy(media = media?.copy(clearing = true)) else this

/** Marks how deleting each instance's files from this page went, so the row says so whatever the server's status now reads. */
private fun RequestDetail.withFilesDeleted(deleted: Map<Boolean, FilesDeletion>): RequestDetail =
    if (deleted.isEmpty()) {
        this
    } else {
        copy(media = media?.copy(instances = media.instances.map { it.copy(filesDeletion = deleted[it.is4k]) }))
    }
