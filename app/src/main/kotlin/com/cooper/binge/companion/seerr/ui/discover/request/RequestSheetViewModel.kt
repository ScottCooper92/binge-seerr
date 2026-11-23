package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.request.ItemSelection
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.discover.request.RequestSheetRepository
import com.cooper.binge.companion.seerr.discover.request.RequestUser
import com.cooper.binge.companion.seerr.discover.request.SeasonSelection
import com.cooper.binge.companion.seerr.discover.request.SelectableItem
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * [problem] is a refusal with a cause and a next step, shown in a dialog with one OK. [failure] is a send that did not get an
 * answer: the footer button turns red for a few seconds ([footerFailed]), and a tap in that time opens [failureDialog] to retry.
 */
sealed interface RequestSheetUiState {
    data object Loading : RequestSheetUiState

    data class Error(
        val error: SeerrError,
    ) : RequestSheetUiState

    data class Ready(
        val target: RequestSheetTarget,
        val access: RequestAccess,
        val quota: QuotaState = QuotaState.Loading,
        val servers: ServersState = ServersState.Loading,
        val use4k: Boolean = false,
        val anime: Boolean = false,
        val pick: DestinationPick? = null,
        val users: UsersState = UsersState.None,
        /** The user the request is made for; null is the one sending it. */
        val requestFor: RequestUser? = null,
        val destinationChanged: Boolean = false,
        val submitting: Boolean = false,
        val problem: RequestProblem? = null,
        val failure: RequestProblem? = null,
        val footerFailed: Boolean = false,
        val failureDialog: Boolean = false,
        val pickedSeasons: Set<Int> = emptySet(),
        val pickedFilms: Set<Int>? = null,
        /** Where each server opens in a browser, by id, for an administrator; empty until read, and where it could not be. */
        val serverUrls: Map<Int, String> = emptyMap(),
    ) : RequestSheetUiState {
        val knownQuota: RequestQuota? get() = (quota as? QuotaState.Known)?.quota

        /** A show's seasons of the chosen version, with what the user ticked; null for a film. */
        val seasons: SeasonSelection?
            get() =
                target.seasonChoices?.let {
                    SeasonSelection(it.of(use4k), target.partialRequests, pickedSeasons, limit = knownQuota?.remaining)
                }

        /**
         * A collection's films with what the user ticked, or what is ticked before they touch it ([pickedFilms] null): every open film
         * where the quota covers them all, none where it cannot. Null for a film or a show.
         */
        val films: ItemSelection?
            get() =
                target.films.takeIf { it.isNotEmpty() }?.let { films ->
                    val base = ItemSelection(films.map { SelectableItem(it.tmdbId, it.open(use4k)) }, limit = knownQuota?.remaining)
                    pickedFilms?.let { base.copy(picked = it) } ?: base.preselected()
                }

        /** The servers of the chosen version: 4K is a property of a server, so the 4K switch narrows this list. */
        val servers4k: List<RequestServer> get() = (servers as? ServersState.Ready)?.servers.orEmpty().filter { it.is4k == use4k }

        /** The destination rows are offered to a user who may choose, unless the servers read and hold none of this version. */
        val choosesDestination: Boolean get() = access.mayChooseDestination && (servers !is ServersState.Ready || servers4k.isNotEmpty())

        /** The quota is the sender's own, which says nothing of the user a request is made for: the server checks theirs. */
        val quotaSpent: Boolean get() = knownQuota?.spent == true && requestFor == null

        /** The 4K switch moves only while both versions are open to request; with one left, that one is the choice. */
        val canToggle4k: Boolean get() = access.may4k && target.standardOpen && target.fourKOpen && !submitting

        /** A read is still in flight: sending now would send defaults the user never saw. A read that failed does not hold the request. */
        val destinationBlocking: Boolean
            get() = choosesDestination && (servers is ServersState.Loading || pick?.choices is ChoicesState.Loading)

        /**
         * The destination the request names, only once the user has changed a row: the whole pick they can see, whose profile and
         * folder are the chosen server's own where they changed only the server. Untouched, the request is a plain one and the
         * server's override rules apply. A pick whose lists failed still names its server.
         */
        val destination: DestinationPick?
            get() = pick?.takeIf { choosesDestination && destinationChanged && it.choices !is ChoicesState.Loading }

        val canSubmit: Boolean get() =
            !submitting &&
                !quotaSpent &&
                !destinationBlocking &&
                (seasons?.canSubmit ?: true) &&
                (films?.canSubmit ?: true)
    }
}

/** [Done] is any answer that leaves nothing more to do: a request made, one already there, or nothing left to ask for. */
sealed interface RequestSheetEvent {
    data class Done(
        val result: RequestResult,
    ) : RequestSheetEvent
}

/** Which of the sheet's reads to try again. */
enum class RequestRead { Quota, Servers, Choices, Users }

@HiltViewModel(assistedFactory = RequestSheetViewModel.Factory::class)
class RequestSheetViewModel
    @AssistedInject
    constructor(
        private val repository: RequestSheetRepository,
        @Assisted private val target: RequestSheetTarget,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(target: RequestSheetTarget): RequestSheetViewModel
        }

        private val store = RequestSheetStore()
        private val reads = RequestSheetReads(repository, target, store, viewModelScope)

        val uiState: StateFlow<RequestSheetUiState> = store.flow

        private val eventFlow = MutableSharedFlow<RequestSheetEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<RequestSheetEvent> = eventFlow.asSharedFlow()

        private val sender =
            RequestSheetSender(repository, target, store, viewModelScope) { result ->
                eventFlow.tryEmit(RequestSheetEvent.Done(result))
                load()
            }

        init {
            load()
        }

        fun load() {
            store.set(RequestSheetUiState.Loading)
            viewModelScope.launch {
                attempt { repository.access(target.kind) }.fold(
                    onSuccess = { access ->
                        val use4k = access.may4k && !target.standardOpen && target.fourKOpen
                        val servers = if (access.mayChooseDestination) ServersState.Loading else ServersState.Ready(emptyList())
                        val users = if (access.mayRequestForOthers) UsersState.Loading else UsersState.None
                        store.set(RequestSheetUiState.Ready(target, access, servers = servers, use4k = use4k, users = users))
                        reads.quota()
                        if (access.mayChooseDestination) reads.servers()
                        if (access.mayRequestForOthers) reads.users()
                        if (access.mayManageServers && access.mayChooseDestination) reads.addresses()
                    },
                    onFailure = { store.set(RequestSheetUiState.Error(it.toSeerrError())) },
                )
            }
        }

        fun retry(read: RequestRead) =
            when (read) {
                RequestRead.Quota -> {
                    store.update { it.copy(quota = QuotaState.Loading) }
                    reads.quota()
                }
                RequestRead.Servers -> {
                    store.update { it.copy(servers = ServersState.Loading) }
                    reads.servers()
                }
                RequestRead.Users -> {
                    store.update { it.copy(users = UsersState.Loading) }
                    reads.users()
                }
                RequestRead.Choices ->
                    store.ready()?.pick?.serverId?.let { serverId ->
                        store.updatePick { it.copy(choices = ChoicesState.Loading) }
                        reads.choices(serverId)
                    }
            }

        fun setUse4k(use4k: Boolean) {
            val current = store.ready() ?: return
            if (current.use4k == use4k || !current.canToggle4k) return
            val pick = (current.servers as? ServersState.Ready)?.servers.orEmpty().pickFor(use4k, current.anime)
            store.set(current.copy(use4k = use4k, pick = pick, destinationChanged = false, pickedSeasons = emptySet(), pickedFilms = null))
            pick?.let { reads.choices(it.serverId) }
        }

        /** Flips one film of a collection, or every open one for `null`. */
        fun toggleFilms(id: Int?) =
            store.update { current ->
                current.films?.let { current.copy(pickedFilms = (id?.let(it::toggle) ?: it.toggleAll()).picked) } ?: current
            }

        /** Flips one season, or every open one for `null`. */
        fun toggleSeasons(number: Int?) =
            store.update { current ->
                current.seasons?.let { current.copy(pickedSeasons = (number?.let(it::toggle) ?: it.toggleAll()).picked) } ?: current
            }

        fun selectServer(id: Int) {
            val current = store.ready() ?: return
            val server = current.servers4k.firstOrNull { it.id == id } ?: return
            if (current.pick?.serverId == id) return
            store.set(
                current.copy(
                    pick = server.pickAt(current.anime),
                    destinationChanged = true,
                ),
            )
            reads.choices(id)
        }

        fun selectProfile(id: Int) = store.update { it.copy(destinationChanged = true, pick = it.pick?.copy(profileId = id)) }

        fun selectRootFolder(path: String) = store.update { it.copy(destinationChanged = true, pick = it.pick?.copy(rootFolder = path)) }

        /** Flips one tag; the first touch starts from the server's defaults, so a default can be taken off as well as another put on. */
        fun toggleTag(id: Int) =
            store.update { current ->
                val pick = current.pick ?: return@update current
                val shown = pick.shownTagIds()
                current.copy(destinationChanged = true, pick = pick.copy(tagIds = if (id in shown) shown - id else shown + id))
            }

        /** Makes the request for an id, or for the sender again. */
        fun selectRequestFor(id: Int) = store.requestFor(id)

        fun dismissProblem() = store.update { it.copy(problem = null) }

        /** A tap on the footer while it shows the failure opens the dialog that explains it and offers Retry. */
        fun openFailure() = store.update { if (it.failure != null) it.copy(failureDialog = true) else it }

        fun dismissFailure() = store.update { it.copy(failureDialog = false, failure = null, footerFailed = false) }

        fun submit() = sender.submit()
    }
