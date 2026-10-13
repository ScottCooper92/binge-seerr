package com.cooper.binge.companion.seerr.ui.requests

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrEditRequestBody
import com.cooper.binge.companion.seerr.seerr.SeerrMediaDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrServerDto
import com.cooper.binge.companion.seerr.seerr.arrServer
import com.cooper.binge.companion.seerr.seerr.arrServers
import com.cooper.binge.companion.seerr.seerr.forRequest
import com.cooper.binge.companion.seerr.seerr.isTv
import com.cooper.binge.companion.seerr.seerr.preferred
import com.cooper.binge.companion.seerr.ui.Choice
import com.cooper.binge.companion.seerr.ui.DestinationChoices
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val FIRST_SEASON = 1

/**
 * What the editor opens on: the request as the server returned it, and its title as the server lists it.
 * [seasonsEditable] is false for a show on a server with partial requests off: the editor then offers
 * no season list. Its save still sends the request's own seasons, because the server refuses a show's
 * edit without them (#1003). [can4k] offers the move between the standard and the 4K version: the user may
 * request 4K of this kind and the server has it on.
 */
class EditSource(
    val request: SeerrRequestDto,
    val details: SeerrMediaDetailsDto?,
    val canEditDestination: Boolean,
    val seasonsEditable: Boolean = true,
    val can4k: Boolean = false,
)

/** What a screen may ask of a [RequestEditor]: its actions, and none of its flows (#1048). */
interface RequestEditorControls {
    fun cancel()

    fun toggleSeason(number: Int)

    fun selectAllSeasons(selected: Boolean)

    fun select4k(is4k: Boolean)

    fun selectServer(id: Int)

    fun selectProfile(id: Int)

    fun selectRootFolder(path: String)

    fun toggleTag(id: Int)

    fun save()
}

/**
 * The editor for one request: which of a show's seasons it covers, and, for a user with
 * `REQUEST_ADVANCED`, where it goes. Saving sends the whole new season set and the destination in
 * one `PUT`; the moderation events say how it went, so the editor closes on [ModerationEvent.Edited]
 * and unlocks again on a failure.
 *
 * Every launch below runs on [dispatcher] rather than `scope`'s own, for the same reason
 * [RequestModeration] does (#177): `loadServers`/`loadChoices` are exactly the "editor's own load"
 * PR #365 found still able to outlive a cleared [scope].
 */
class RequestEditor(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val connection: SeerrConnection,
    private val moderation: RequestModeration,
) : RequestEditorControls {
    private val edit = MutableStateFlow<EditState?>(null)
    val state: StateFlow<EditState?> = edit.asStateFlow()

    private var source: EditSource? = null

    /** @Volatile: written by [loadServers] on [dispatcher], read by [selectServer] on the caller's thread. */
    @Volatile
    private var servers: List<SeerrServerDto> = emptyList()

    init {
        scope.launch(dispatcher) {
            moderation.events.collect { event ->
                when (event) {
                    ModerationEvent.Edited -> edit.value = null
                    is ModerationEvent.Failed -> edit.update { it?.copy(saving = false, failed = true) }
                    else -> Unit
                }
            }
        }
    }

    fun start(source: EditSource) {
        if (edit.value != null) return
        this.source = source
        val request = source.request
        val destination = if (source.canEditDestination) request.destination().copy(loadingChoices = true) else null
        val seasonsUnknown = request.isTv && source.seasonsEditable && source.details == null
        edit.value =
            EditState(
                seasons = if (source.seasonsEditable) source.seasonChoices(request.is4k) else emptyList(),
                destination = destination,
                seasonsUnknown = seasonsUnknown,
                seasonsEditable = source.seasonsEditable,
                is4k = request.is4k,
                can4k = source.can4k && destination != null,
            )
        if (destination != null) scope.launch(dispatcher) { loadServers(request.is4k) }
    }

    override fun cancel() {
        edit.value = null
    }

    override fun toggleSeason(number: Int) =
        update { state ->
            state.copy(seasons = state.seasons.map { if (it.number == number && !it.locked) it.copy(selected = !it.selected) else it })
        }

    /** Ticks or clears every season the editor may change; a held season is left exactly as it is. */
    override fun selectAllSeasons(selected: Boolean) =
        update { state -> state.copy(seasons = state.seasons.map { if (it.locked) it else it.copy(selected = selected) }) }

    /**
     * Moves the request to the other version. Its servers are that version's, so the destination starts over from
     * its default, and a season's hold is read again, since what the server holds differs by version. The ticks stay,
     * bar a held season's, which is the request's own again.
     */
    override fun select4k(is4k: Boolean) {
        val source = source ?: return
        var changed = false
        update { state ->
            if (!state.can4k || state.saving || state.is4k == is4k) return@update state
            changed = true
            val ticked = state.seasons.associate { it.number to it.selected }
            state.copy(
                is4k = is4k,
                seasons = if (state.seasons.isEmpty()) state.seasons else source.seasonChoices(is4k).keepTicks(ticked),
                destination = DestinationChoices(loadingChoices = true),
            )
        }
        if (changed) scope.launch(dispatcher) { loadServers(is4k) }
    }

    override fun selectServer(id: Int) {
        val server = servers.firstOrNull { it.id == id } ?: return
        var changed = false
        updateDestination { destination ->
            if (destination.serverId == id) return@updateDestination destination
            changed = true
            destination.onServer(server)
        }
        if (changed) scope.launch(dispatcher) { loadChoices(id) }
    }

    override fun selectProfile(id: Int) = updateDestination { it.copy(profileId = id) }

    override fun selectRootFolder(path: String) = updateDestination { it.copy(rootFolder = path) }

    override fun toggleTag(id: Int) = updateDestination { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }

    override fun save() {
        val state = edit.value ?: return
        val request = source?.request ?: return
        if (!state.canSave) return
        edit.value = state.copy(saving = true, failed = false)
        moderation.edit(request.id, state.toBody(request))
    }

    /**
     * The list a request of this shape may go to; the request's own server where it is still listed, else the default.
     * A load for the version the editor has since moved off is dropped.
     */
    private suspend fun loadServers(is4k: Boolean) {
        val request = source?.request ?: return
        val loaded = attempt { connection.api().arrServers(request.isTv).forRequest(is4k) }.getOrNull()
        if (edit.value?.is4k != is4k) return
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
        val details = attempt { connection.api().arrServer(request.isTv, serverId) }.getOrNull()
        updateDestination { destination ->
            when {
                destination.serverId != serverId -> destination
                details == null -> destination.copy(loadingChoices = false)
                else -> destination.withChoices(details)
            }
        }
    }

    /**
     * The server assigns the whole destination from the body, so a `PUT` that leaves one of its
     * fields out clears it. A user who may not change the destination still edits seasons through
     * here, and their request's own destination is what goes back with it. Seasons are the same the
     * other way round: both lineages answer a show's `PUT` without them with a 500, so where the
     * editor offers no season list, the request's own seasons go back.
     */
    private fun EditState.toBody(request: SeerrRequestDto): SeerrEditRequestBody {
        val destination = destination ?: request.destination()
        return SeerrEditRequestBody(
            mediaType = request.media.mediaType,
            seasons =
                when {
                    !request.isTv -> null
                    // Every season ticked, held ones included: the server keeps only the seasons the body names (#1022).
                    seasonsEditable && !seasonsUnknown -> seasons.filter { it.selected }.map { it.number }
                    else -> request.seasons.map { it.seasonNumber }
                },
            is4k = is4k,
            serverId = destination.serverId,
            profileId = destination.profileId,
            rootFolder = destination.rootFolder,
            tags = destination.tagIds.toList(),
        )
    }

    private fun update(transform: (EditState) -> EditState) = edit.update { it?.let(transform) }

    private fun updateDestination(transform: (DestinationChoices) -> DestinationChoices) =
        update { state -> state.copy(destination = state.destination?.let(transform)) }
}

/** Where the request already goes: what the editor opens on, and what a body that does not change it repeats. */
private fun SeerrRequestDto.destination(): DestinationChoices =
    DestinationChoices(
        serverId = serverId,
        profileId = profileId,
        rootFolder = rootFolder,
        tagIds = tags.orEmpty().toSet(),
    )

/**
 * A show's seasons as the checklist: specials and empty seasons are left out, this request's own
 * seasons start ticked, and a season the server holds or another live request covers is locked.
 */
private fun EditSource.seasonChoices(is4k: Boolean): List<SeasonChoice> {
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
                heldStatus = details.heldStatus(season.seasonNumber, request.id, is4k),
            )
        }
}

/** These choices with the user's ticks carried over, where the season is still theirs to tick. */
private fun List<SeasonChoice>.keepTicks(ticked: Map<Int, Boolean>): List<SeasonChoice> =
    map { if (it.locked) it else it.copy(selected = ticked[it.number] ?: it.selected) }

/** A request that is neither declined nor failed still occupies the season it covers. */
private fun SeerrRequestStatusCode?.isLive(): Boolean = this != SeerrRequestStatusCode.Declined && this != SeerrRequestStatusCode.Failed

private fun SeerrMediaDetailsDto.heldStatus(
    seasonNumber: Int,
    requestId: Int,
    is4k: Boolean,
): SeerrMediaStatusCode? {
    val info = mediaInfo ?: return null
    val season = info.seasons.firstOrNull { it.seasonNumber == seasonNumber }
    val status = if (is4k) season?.status4k else season?.status
    if (status == SeerrMediaStatusCode.Processing ||
        status == SeerrMediaStatusCode.PartiallyAvailable ||
        status == SeerrMediaStatusCode.Available
    ) {
        return status
    }
    val elsewhere =
        info.requests.any { other ->
            other.id != requestId &&
                other.is4k == is4k &&
                other.status.isLive() &&
                other.seasons.any { it.seasonNumber == seasonNumber }
        }
    return if (elsewhere) SeerrMediaStatusCode.Pending else null
}
