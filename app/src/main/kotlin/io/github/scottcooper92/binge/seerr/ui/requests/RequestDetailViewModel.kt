package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.HydratedTitle
import io.github.scottcooper92.binge.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrCreateIssueBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaDetailsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrWatchDataDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrWatchStatsDto
import io.github.scottcooper92.binge.seerr.seerr.arrServer
import io.github.scottcooper92.binge.seerr.seerr.arrServers
import io.github.scottcooper92.binge.seerr.seerr.details
import io.github.scottcooper92.binge.seerr.seerr.downloadFraction
import io.github.scottcooper92.binge.seerr.seerr.etaMinutes
import io.github.scottcooper92.binge.seerr.seerr.isWebUrl
import io.github.scottcooper92.binge.seerr.seerr.toEpochMillisOrNull
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
                .stateIn(viewModelScope, SharingStarted.Lazily, RequestDetailUiState.Loading)

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

        private suspend fun load(): RequestDetail {
            val sources = fetchSources()
            editSource =
                EditSource(
                    request = sources.dto,
                    details = sources.details,
                    canEditDestination = sources.canEditDestination,
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
                val user = async { runCatching { connection.authenticatedUser() }.getOrNull() }
                val permissions = async { user.await().toPermissions() }
                val dto = api.request(requestId)
                val details = async { runCatching { api.details(dto.media.mediaType, dto.media.tmdbId) }.getOrNull() }
                val destination = async { dto.destination(api) }
                val watch =
                    async {
                        val mediaId = dto.media.id
                        if (mediaId != null && permissions.await().isAdmin && profile.await().hasWatchData) {
                            runCatching { api.watchData(mediaId) }.getOrNull()
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
            val servers = runCatching { api.arrServers(notMovie) }.getOrNull()
            val details = runCatching { api.arrServer(notMovie, serverId) }.getOrNull()
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

private fun SeerrWatchStatsDto.toWatchStats(): WatchStats =
    WatchStats(
        playCount = playCount,
        playCount7Days = playCount7Days,
        playCount30Days = playCount30Days,
        users = users.mapNotNull { user -> listOfNotNull(user.displayName, user.username).firstOrNull { it.isNotBlank() } },
    )

/**
 * The server's answers for one request page, before they are shaped into a [RequestDetail]. Split
 * from the fetch so the page's rules read as rules rather than as one long constructor call.
 */
private class DetailSources(
    val api: SeerrApi,
    val dto: SeerrRequestDto,
    val details: SeerrMediaDetailsDto?,
    val profile: SeerrServerProfile,
    val user: SeerrUserDto?,
    val permissions: SeerrPermissions,
    val destination: RequestDestination?,
    val watch: SeerrWatchDataDto?,
    val webRoot: String,
) {
    val scope: ModerationScope
        get() = ModerationScope(permissions, currentUserId = user?.id, hasBlocklist = profile.hasBlocklist)

    /** A request the server has not answered yet, which is what makes it editable at all. */
    val pending: Boolean get() = dto.status == null || dto.status == SeerrRequestStatusCode.Pending

    val canEditDestination: Boolean get() = pending && permissions.canRequestAdvanced

    suspend fun toDetail(): RequestDetail {
        val hydrated = details?.let { HydratedTitle(it.displayTitle, it.posterPath?.toTmdbPosterUrl(), it.year) }
        val item =
            checkNotNull(dto.toRequestItem(api, { _, _, _ -> hydrated }, System.currentTimeMillis())) {
                "Unrenderable media type"
            }
        val own = dto.requestedBy?.id != null && dto.requestedBy.id == user?.id
        return RequestDetail(
            item = item,
            actions = item.actions(scope),
            canEdit = pending && (permissions.canManageRequests || own),
            canEditDestination = canEditDestination,
            backdropUrl = details?.backdropPath?.toTmdbBackdropUrl(),
            overview = details?.overview?.takeIf { it.isNotBlank() },
            modifiedBy = dto.modifiedByName(),
            updatedAtMillis = dto.updatedAt?.toEpochMillisOrNull(),
            seasons = seasons(),
            destination = destination,
            downloads = downloads(),
            mediaId = dto.media.id,
            canReportIssue = profile.hasIssues && permissions.canCreateIssues && dto.media.id != null,
            webUrl = webRoot + dto.media.mediaType + "/" + dto.media.tmdbId,
            mediaServerUrl = preferred(dto.media.mediaUrl, dto.media.mediaUrl4k),
            serviceUrl = preferred(dto.media.serviceUrl, dto.media.serviceUrl4k),
            media = dto.mediaRecord(permissions, profile, watch),
        )
    }

    /** The season the request asked for, named from the title's own list where that loaded. */
    private fun seasons(): List<SeasonState> =
        dto.seasons.map { requested ->
            val season = details?.seasons?.firstOrNull { it.seasonNumber == requested.seasonNumber }
            SeasonState(requested.seasonNumber, season?.name, season?.episodeCount, requested.status)
        }

    private fun downloads(): List<DetailDownload> {
        val statuses = if (dto.is4k) dto.media.downloadStatus4k else dto.media.downloadStatus
        val now = System.currentTimeMillis()
        return statuses.map { status ->
            DetailDownload(
                title = status.title,
                fraction = listOf(status).downloadFraction(),
                totalBytes = status.size?.toLong()?.takeIf { it > 0 },
                etaMinutes = listOf(status).etaMinutes(now),
            )
        }
    }

    /** A 4K request prefers the 4K link and falls back to the standard one; anything else takes the standard. */
    private fun preferred(
        standard: String?,
        fourK: String?,
    ): String? = (if (dto.is4k) fourK ?: standard else standard)?.takeIf { it.isWebUrl() }
}

/** The name the server shows for whoever last changed the request, preferring the display name. */
private fun SeerrRequestDto.modifiedByName(): String? =
    modifiedBy?.let { listOfNotNull(it.displayName, it.username).firstOrNull { name -> name.isNotBlank() } }

/** The 4K instance is listed only where the server holds one, or the request itself is 4K. */
private fun SeerrRequestDto.mediaRecord(
    permissions: SeerrPermissions,
    profile: SeerrServerProfile,
    watch: SeerrWatchDataDto?,
): MediaRecord? {
    val mediaId = media.id ?: return null
    val has4k = is4k || (media.status4k != null && media.status4k != SeerrMediaStatusCode.Unknown)
    return MediaRecord(
        mediaId = mediaId,
        isTv = media.mediaType != SEERR_MEDIA_TYPE_MOVIE,
        instances =
            listOfNotNull(
                MediaInstance(
                    false,
                    media.status,
                    media.serviceUrl?.takeIf { it.isWebUrl() },
                    media.mediaUrl?.takeIf { it.isWebUrl() },
                    watch?.data?.toWatchStats(),
                ),
                MediaInstance(
                    true,
                    media.status4k,
                    media.serviceUrl4k?.takeIf { it.isWebUrl() },
                    media.mediaUrl4k?.takeIf { it.isWebUrl() },
                    watch?.data4k?.toWatchStats(),
                ).takeIf { has4k },
            ),
        canSetStatus = permissions.canManageRequests,
        canClearData = permissions.canManageRequests,
        canDeleteFiles = permissions.canManageRequests && profile.hasDeleteMediaFiles,
    )
}
