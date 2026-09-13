package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.HydratedTitle
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrCreateIssueBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerDetailsDto
import io.github.scottcooper92.binge.seerr.seerr.details
import io.github.scottcooper92.binge.seerr.seerr.downloadFraction
import io.github.scottcooper92.binge.seerr.seerr.etaMinutes
import io.github.scottcooper92.binge.seerr.seerr.isWebUrl
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.seerr.toTmdbBackdropUrl
import io.github.scottcooper92.binge.seerr.seerr.toTmdbPosterUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime

private const val MEDIA_TYPE_MOVIE = "movie"

/**
 * One request as a page. The request itself, its title's lookup, and the destination's names are
 * read together on open; the destination and the lookup are best-effort, so a title the server no
 * longer tracks or a service it has since removed still shows the request.
 */
@HiltViewModel(assistedFactory = RequestDetailViewModel.Factory::class)
class RequestDetailViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @Assisted private val requestId: Int,
    ) : ViewModel() {
        private val state = MutableStateFlow<RequestDetailUiState>(RequestDetailUiState.Loading)

        /** A moderation reloads the page, so the chip and the history show the server's new answer. */
        val moderation = RequestModeration(scope = viewModelScope, connection = connection, onModerated = ::reload)

        /** The editor rides the page's state while it is open; it closes itself on the save landing. */
        val editor = RequestEditor(scope = viewModelScope, connection = connection, moderation = moderation)

        val uiState: StateFlow<RequestDetailUiState> =
            combine(state, editor.state) { page, edit -> (page as? RequestDetailUiState.Ready)?.copy(edit = edit) ?: page }
                .stateIn(viewModelScope, SharingStarted.Eagerly, RequestDetailUiState.Loading)

        private var editSource: EditSource? = null

        init {
            reload()
        }

        fun startEdit() {
            val ready = state.value as? RequestDetailUiState.Ready ?: return
            if (ready.detail.canEdit) editSource?.let(editor::start)
        }

        fun reload() {
            if (state.value !is RequestDetailUiState.Ready) state.value = RequestDetailUiState.Loading
            viewModelScope.launch {
                state.value =
                    runCatching { load() }
                        .fold({ RequestDetailUiState.Ready(it) }, { RequestDetailUiState.Error(it.toSeerrError()) })
            }
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
            viewModelScope.launch {
                val outcome =
                    runCatching { connection.api().createIssue(SeerrCreateIssueBody(mediaId, type.code, message.trim())) }
                        .fold({ IssueReport.Sent }, { IssueReport.Failed(it.toSeerrError()) })
                state.update { current -> (current as? RequestDetailUiState.Ready)?.copy(report = outcome) ?: current }
            }
        }

        /** A dismiss while a send is in flight only hides the sheet; it must not clear [IssueReport.Sending], or reopening it loses [reportIssue]'s re-entrancy guard and lets a second POST fire. */
        fun dismissReport() =
            state.update { current ->
                val ready = current as? RequestDetailUiState.Ready ?: return@update current
                if (ready.report == IssueReport.Sending) ready else ready.copy(report = IssueReport.Idle)
            }

        private suspend fun load(): RequestDetail =
            coroutineScope {
                val api = connection.api()
                val profile = async { connection.profile() }
                val user = async { runCatching { connection.authenticatedUser() }.getOrNull() }
                val permissions = async { user.await().toPermissions() }
                val dto = api.request(requestId)
                val details = async { runCatching { api.details(dto.media.mediaType, dto.media.tmdbId) }.getOrNull() }
                val destination = async { dto.destination(api) }
                val detailsDto = details.await()
                val hydrated = detailsDto?.let { HydratedTitle(it.displayTitle, it.posterPath?.toTmdbPosterUrl(), it.year) }
                val item =
                    checkNotNull(dto.toRequestItem(api, { _, _, _ -> hydrated }, System.currentTimeMillis())) {
                        "Unrenderable media type"
                    }
                val statuses = if (dto.is4k) dto.media.downloadStatus4k else dto.media.downloadStatus
                val scope =
                    ModerationScope(
                        permissions.await(),
                        currentUserId = user.await()?.id,
                        hasBlocklist = profile.await().hasBlocklist,
                    )
                val pending = dto.status == null || dto.status == SeerrRequestStatusCode.Pending
                val own = dto.requestedBy?.id != null && dto.requestedBy.id == scope.currentUserId
                val canEditDestination = pending && scope.permissions.canRequestAdvanced
                editSource = EditSource(request = dto, details = detailsDto, canEditDestination = canEditDestination)
                RequestDetail(
                    item = item,
                    actions = item.actions(scope),
                    canEdit = pending && (scope.permissions.canManageRequests || own),
                    canEditDestination = canEditDestination,
                    backdropUrl = detailsDto?.backdropPath?.toTmdbBackdropUrl(),
                    overview = detailsDto?.overview?.takeIf { it.isNotBlank() },
                    modifiedBy =
                        dto.modifiedBy?.let {
                            listOfNotNull(it.displayName, it.username).firstOrNull { name ->
                                name.isNotBlank()
                            }
                        },
                    updatedAtMillis = dto.updatedAt?.toEpochMillisOrNull(),
                    seasons =
                        dto.seasons.map { requested ->
                            val season = detailsDto?.seasons?.firstOrNull { it.seasonNumber == requested.seasonNumber }
                            SeasonState(requested.seasonNumber, season?.name, season?.episodeCount, requested.status)
                        },
                    destination = destination.await(),
                    downloads =
                        statuses.map { status ->
                            DetailDownload(
                                title = status.title,
                                fraction = listOf(status).downloadFraction(),
                                totalBytes = status.size?.toLong()?.takeIf { it > 0 },
                                etaMinutes = listOf(status).etaMinutes(System.currentTimeMillis()),
                            )
                        },
                    mediaId = dto.media.id,
                    canReportIssue = profile.await().hasIssues && permissions.await().canCreateIssues && dto.media.id != null,
                    webUrl = connection.current().baseUrl + dto.media.mediaType + "/" + dto.media.tmdbId,
                    mediaServerUrl = dto.media.mediaUrl?.takeIf { it.isWebUrl() },
                )
            }

        /** The service lists name the ids the request carries; a service the admin removed leaves the id unnamed. */
        private suspend fun SeerrRequestDto.destination(api: SeerrApi): RequestDestination? {
            val serverId = serverId ?: return null
            val isMovie = media.mediaType == MEDIA_TYPE_MOVIE
            val servers = runCatching { if (isMovie) api.radarrServers() else api.sonarrServers() }.getOrNull()
            val details: SeerrServerDetailsDto? =
                runCatching {
                    if (isMovie) {
                        api.radarrServer(
                            serverId,
                        )
                    } else {
                        api.sonarrServer(serverId)
                    }
                }.getOrNull()
            return RequestDestination(
                serverName = servers?.firstOrNull { it.id == serverId }?.name ?: details?.server?.name,
                profileName = details?.profiles?.firstOrNull { it.id == profileId }?.name,
                rootFolder = rootFolder,
                tags = tags.mapNotNull { id -> details?.tags?.firstOrNull { it.id == id }?.label },
            )
        }

        @AssistedFactory
        interface Factory {
            fun create(requestId: Int): RequestDetailViewModel
        }
    }

private fun String.toEpochMillisOrNull(): Long? =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
