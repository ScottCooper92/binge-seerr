package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrEditRequestBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaDetailsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerDetailsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerDto
import io.github.scottcooper92.binge.seerr.seerr.forRequest
import io.github.scottcooper92.binge.seerr.seerr.preferred
import io.github.scottcooper92.binge.seerr.ui.Choice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MEDIA_TYPE_TV = "tv"
private const val FIRST_SEASON = 1

/** What the editor opens on: the request as the server returned it, and its title as the server lists it. */
class EditSource(
    val request: SeerrRequestDto,
    val details: SeerrMediaDetailsDto?,
    val canEditDestination: Boolean,
)

/**
 * The editor for one request: which of a show's seasons it covers, and, for a user with
 * `REQUEST_ADVANCED`, where it goes. Saving sends the whole new season set and the destination in
 * one `PUT`; the moderation events say how it went, so the editor closes on [ModerationEvent.Edited]
 * and unlocks again on a failure.
 */
class RequestEditor(
    private val scope: CoroutineScope,
    private val connection: SeerrConnection,
    private val moderation: RequestModeration,
) {
    private val edit = MutableStateFlow<EditState?>(null)
    val state: StateFlow<EditState?> = edit.asStateFlow()

    private var source: EditSource? = null
    private var servers: List<SeerrServerDto> = emptyList()

    init {
        scope.launch {
            moderation.events.collect { event ->
                when (event) {
                    ModerationEvent.Edited -> edit.value = null
                    is ModerationEvent.Failed -> edit.update { it?.copy(saving = false) }
                    else -> Unit
                }
            }
        }
    }

    fun start(source: EditSource) {
        if (edit.value != null) return
        this.source = source
        val request = source.request
        val destination =
            if (source.canEditDestination) {
                DestinationChoices(
                    servers = emptyList(),
                    serverId = request.serverId,
                    profiles = emptyList(),
                    profileId = request.profileId,
                    rootFolders = emptyList(),
                    rootFolder = request.rootFolder,
                    tags = emptyList(),
                    tagIds = request.tags.toSet(),
                    loadingChoices = true,
                )
            } else {
                null
            }
        edit.value = EditState(seasons = source.seasonChoices(), destination = destination)
        if (destination != null) scope.launch { loadServers(request) }
    }

    fun cancel() {
        edit.value = null
    }

    fun toggleSeason(number: Int) =
        update { state ->
            state.copy(seasons = state.seasons.map { if (it.number == number && !it.locked) it.copy(selected = !it.selected) else it })
        }

    fun selectServer(id: Int) {
        val server = servers.firstOrNull { it.id == id } ?: return
        var changed = false
        updateDestination { destination ->
            if (destination.serverId == id) return@updateDestination destination
            changed = true
            destination.copy(
                serverId = id,
                profiles = emptyList(),
                profileId = server.activeProfileId,
                rootFolders = emptyList(),
                rootFolder = server.activeDirectory,
                tags = emptyList(),
                tagIds = emptySet(),
                loadingChoices = true,
            )
        }
        if (changed) scope.launch { loadChoices(id) }
    }

    fun selectProfile(id: Int) = updateDestination { it.copy(profileId = id) }

    fun selectRootFolder(path: String) = updateDestination { it.copy(rootFolder = path) }

    fun toggleTag(id: Int) = updateDestination { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }

    fun save() {
        val state = edit.value ?: return
        val request = source?.request ?: return
        if (!state.canSave) return
        edit.value = state.copy(saving = true)
        moderation.edit(request.id, state.toBody(request))
    }

    /** The list a request of this shape may go to; the request's own server where it is still listed, else the default. */
    private suspend fun loadServers(request: SeerrRequestDto) {
        val loaded = runCatching { connection.api().servers(request).forRequest(request.is4k) }.getOrNull()
        if (loaded == null) {
            updateDestination { it.copy(loadingChoices = false) }
            return
        }
        servers = loaded
        val serverId = loaded.firstOrNull { it.id == request.serverId }?.id ?: loaded.preferred()?.id
        updateDestination { it.copy(servers = loaded.map { server -> Choice(server.id, server.name) }, serverId = serverId) }
        if (serverId == null) updateDestination { it.copy(loadingChoices = false) } else loadChoices(serverId)
    }

    /** Fills the chosen server's choices in, unless the user has moved to another server meanwhile. */
    private suspend fun loadChoices(serverId: Int) {
        val request = source?.request ?: return
        val details = runCatching { connection.api().server(request, serverId) }.getOrNull()
        updateDestination { destination ->
            when {
                destination.serverId != serverId -> destination
                details == null -> destination.copy(loadingChoices = false)
                else -> destination.withChoices(details)
            }
        }
    }

    private fun DestinationChoices.withChoices(details: SeerrServerDetailsDto): DestinationChoices =
        copy(
            profiles = details.profiles.map { Choice(it.id, it.name) },
            profileId = details.profiles.firstOrNull { it.id == profileId }?.id ?: details.profiles.firstOrNull()?.id ?: profileId,
            rootFolders = details.rootFolders.map { it.path },
            rootFolder =
                details.rootFolders.firstOrNull { it.path == rootFolder }?.path ?: details.rootFolders.firstOrNull()?.path ?: rootFolder,
            tags = details.tags.map { Choice(it.id, it.label) },
            tagIds = tagIds.filter { id -> details.tags.any { it.id == id } }.toSet(),
            loadingChoices = false,
        )

    private fun EditState.toBody(request: SeerrRequestDto): SeerrEditRequestBody =
        SeerrEditRequestBody(
            mediaType = request.media.mediaType,
            seasons =
                if (request.media.mediaType ==
                    MEDIA_TYPE_TV
                ) {
                    seasons.filter { it.selected && !it.locked }.map { it.number }
                } else {
                    null
                },
            is4k = request.is4k,
            serverId = destination?.serverId,
            profileId = destination?.profileId,
            rootFolder = destination?.rootFolder,
            tags = destination?.tagIds?.toList(),
        )

    private fun update(transform: (EditState) -> EditState) = edit.update { it?.let(transform) }

    private fun updateDestination(transform: (DestinationChoices) -> DestinationChoices) =
        update { state -> state.copy(destination = state.destination?.let(transform)) }

    private suspend fun SeerrApi.servers(request: SeerrRequestDto): List<SeerrServerDto> =
        if (request.media.mediaType == MEDIA_TYPE_TV) sonarrServers() else radarrServers()

    private suspend fun SeerrApi.server(
        request: SeerrRequestDto,
        id: Int,
    ): SeerrServerDetailsDto = if (request.media.mediaType == MEDIA_TYPE_TV) sonarrServer(id) else radarrServer(id)
}

/**
 * A show's seasons as the checklist: specials and empty seasons are left out, this request's own
 * seasons start ticked, and a season the server holds or another live request covers is locked.
 */
private fun EditSource.seasonChoices(): List<SeasonChoice> {
    val details = details ?: return emptyList()
    val requested = request.seasons.map { it.seasonNumber }.toSet()
    return details.seasons
        .filter { it.seasonNumber >= FIRST_SEASON && it.episodeCount > 0 }
        .map { season ->
            SeasonChoice(
                number = season.seasonNumber,
                name = season.name,
                episodeCount = season.episodeCount,
                selected = season.seasonNumber in requested,
                heldStatus = details.heldStatus(season.seasonNumber, request.id),
            )
        }
}

private fun SeerrMediaDetailsDto.heldStatus(
    seasonNumber: Int,
    requestId: Int,
): SeerrMediaStatusCode? {
    val info = mediaInfo ?: return null
    val status = info.seasons.firstOrNull { it.seasonNumber == seasonNumber }?.status
    if (status == SeerrMediaStatusCode.Processing ||
        status == SeerrMediaStatusCode.PartiallyAvailable ||
        status == SeerrMediaStatusCode.Available
    ) {
        return status
    }
    val elsewhere =
        info.requests.any { other ->
            other.id != requestId &&
                other.status != SeerrRequestStatusCode.Declined &&
                other.seasons.any { it.seasonNumber == seasonNumber }
        }
    return if (elsewhere) SeerrMediaStatusCode.Pending else null
}
