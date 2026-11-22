package com.cooper.binge.companion.seerr.ui.discover.request

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.discover.request.RequestSheetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The sheet's one state, with the updates that only apply once it is [RequestSheetUiState.Ready]. */
internal class RequestSheetStore {
    private val state = MutableStateFlow<RequestSheetUiState>(RequestSheetUiState.Loading)
    val flow: StateFlow<RequestSheetUiState> = state.asStateFlow()

    fun set(value: RequestSheetUiState) {
        state.value = value
    }

    fun ready(): RequestSheetUiState.Ready? = state.value as? RequestSheetUiState.Ready

    fun update(transform: (RequestSheetUiState.Ready) -> RequestSheetUiState.Ready) =
        state.update { (it as? RequestSheetUiState.Ready)?.let(transform) ?: it }

    fun updatePick(transform: (DestinationPick) -> DestinationPick) =
        update { current -> current.copy(pick = current.pick?.let(transform)) }
}

/**
 * Makes the request for [id], or for the sender again where it is their own id, which the users read need not hold.
 * Any other id the read does not hold changes nothing.
 */
internal fun RequestSheetStore.requestFor(id: Int) =
    update { current ->
        val chosen = (current.users as? UsersState.Ready)?.users?.firstOrNull { it.id == id }
        when {
            id == current.access.viewerId -> current.copy(requestFor = null)
            chosen == null -> current
            else -> current.copy(requestFor = chosen)
        }
    }

/** The reads that fill the sheet after it opens. Each lands in the state alone, so one failing leaves the others. */
internal class RequestSheetReads(
    private val repository: RequestSheetRepository,
    private val target: RequestSheetTarget,
    private val store: RequestSheetStore,
    private val scope: CoroutineScope,
) {
    fun quota() {
        scope.launch {
            val quota =
                attempt { repository.quota(target.kind) }.fold(
                    onSuccess = { QuotaState.Known(it) },
                    onFailure = { QuotaState.Failed },
                )
            store.update { it.copy(quota = quota) }
        }
    }

    fun users() {
        scope.launch {
            attempt { repository.users() }.fold(
                onSuccess = { users -> store.update { it.copy(users = UsersState.Ready(users)) } },
                onFailure = { store.update { it.copy(users = UsersState.Failed) } },
            )
        }
    }

    fun servers() {
        scope.launch {
            attempt { repository.servers(target.kind) }.fold(
                onSuccess = { servers ->
                    val pick = servers.pickFor(store.ready()?.use4k ?: false)
                    store.update { it.copy(servers = ServersState.Ready(servers), pick = pick) }
                    pick?.let { choices(it.serverId) }
                },
                onFailure = { store.update { it.copy(servers = ServersState.Failed) } },
            )
        }
    }

    /** Applies [change] to the pick only while it is still for [serverId], so a late answer for a server the user left changes nothing. */
    private fun forServer(
        serverId: Int,
        change: (DestinationPick) -> DestinationPick,
    ) = store.updatePick { pick -> if (pick.serverId == serverId) change(pick) else pick }

    fun choices(serverId: Int) {
        scope.launch {
            attempt { repository.choices(target.kind, serverId) }.fold(
                onSuccess = { loaded -> forServer(serverId) { it.withChoices(loaded) } },
                onFailure = { forServer(serverId) { it.copy(choices = ChoicesState.Failed) } },
            )
        }
    }
}

/** The preselected server of a version: the server's default, else its first; null where the user has none to choose. */
internal fun List<RequestServer>.pickFor(use4k: Boolean): DestinationPick? {
    val ofVersion = filter { it.is4k == use4k }
    val server = ofVersion.firstOrNull { it.isDefault } ?: ofVersion.firstOrNull() ?: return null
    return DestinationPick(server.id, server.profileId, server.rootFolder, ChoicesState.Loading)
}
