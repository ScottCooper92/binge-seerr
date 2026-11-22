package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitleDetailsRepository
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.ui.requests.SheetScope
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface TitlePageUiState {
    data object Loading : TitlePageUiState

    data class Error(
        val error: SeerrError,
    ) : TitlePageUiState

    /** [extras] is null until the sections that load apart have arrived, and a section that failed is simply empty in it. */
    data class Ready(
        val movie: MovieDetails,
        val extras: TitleExtras? = null,
        val requestSheetOpen: Boolean = false,
        /** The request sheet was opened by Request in 4K, so it starts on that version whatever the standard one still allows. */
        val requestSheetFourK: Boolean = false,
        /** The request the primary last opened to manage. Kept after the sheet closes, so a moderation's result still has its sheet to land in. */
        val manageRequestId: Int? = null,
        val manageSheetOpen: Boolean = false,
        /** The list of the title's requests, where there are several; a row of it opens that request's sheet. */
        val requestsSheetOpen: Boolean = false,
        /** The request's sheet was opened from the list of requests, so it has a Back to it. */
        val manageFromList: Boolean = false,
        /** Which of the title's and the request's rows the request's sheet holds: both for one request, the title's for several. */
        val manageScope: SheetScope = SheetScope.Both,
        /** The services sheet, opened from the page's Services action. */
        val serviceSheetOpen: Boolean = false,
        val statusSheetOpen: Boolean = false,
        val reportSheetOpen: Boolean = false,
        /** A watchlist call is in flight, so another tap is ignored. */
        val watchlistBusy: Boolean = false,
        /** The confirm for Block is up. */
        val blockConfirmOpen: Boolean = false,
        val collectionParts: List<TitleResult> = emptyList(),
    ) : TitlePageUiState {
        /** The Request button shows while either version can still be asked for. */
        val mayRequest: Boolean get() = movie.standard.mayRequest || movie.fourK?.mayRequest == true
    }
}

/** The sheets over a title page that a screen closes by name. */
enum class TitleSheet { Manage, Status }

sealed interface TitlePageEvent {
    /** A request went through, for the screen's snackbar. */
    data class Requested(
        val result: RequestResult,
    ) : TitlePageEvent

    /** The read after a request failed: the page keeps what it had, and the screen offers to try again. */
    data object ReloadFailed : TitlePageEvent

    /** The title came off the blocklist, for the screen's snackbar; the page reads it again. */
    data object Unblocked : TitlePageEvent

    /** The title was blocked, for the screen's snackbar; the page reads it again. */
    data object Blocked : TitlePageEvent

    /** The server refused or could not be reached, so the title is not blocked. */
    data class BlockFailed(
        val error: SeerrError,
    ) : TitlePageEvent

    /** The watchlist call failed, so the button is back where it was; the screen says so. */
    data class WatchlistFailed(
        val error: SeerrError,
    ) : TitlePageEvent

    /** The server refused or could not be reached, so the title stays blocklisted. */
    data class UnblockFailed(
        val error: SeerrError,
    ) : TitlePageEvent
}

/** The film page. Detail keyed on an id, so it shares [Lazily][kotlinx.coroutines.flow.SharingStarted.Lazily] with the other detail pages: it loads once. */
@HiltViewModel(assistedFactory = TitlePageViewModel.Factory::class)
class TitlePageViewModel
    @AssistedInject
    constructor(
        private val repository: TitleDetailsRepository,
        @Assisted private val tmdbId: Int,
        private val analytics: Analytics = NoOpAnalytics,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(tmdbId: Int): TitlePageViewModel
        }

        private val state = MutableStateFlow<TitlePageUiState>(TitlePageUiState.Loading)
        val uiState: StateFlow<TitlePageUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<TitlePageEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<TitlePageEvent> = eventFlow.asSharedFlow()

        init {
            load()
        }

        fun load() {
            state.value = TitlePageUiState.Loading
            viewModelScope.launch {
                attempt { repository.movie(tmdbId) }.fold(
                    onSuccess = { movie ->
                        val extrasLoad = async { attempt { repository.extras(TitleKind.Movie, tmdbId) }.getOrNull() }
                        val extras = withTimeoutOrNull(HERO_SETTLE_MS) { extrasLoad.await() }
                        state.value = TitlePageUiState.Ready(movie, extras = extras)
                        movie.collection?.let { loadCollection(it.id) }
                        if (extras == null) loadLateExtras(extrasLoad)
                    },
                    onFailure = { state.value = TitlePageUiState.Error(it.toSeerrError()) },
                )
            }
        }

        /** The primary was tapped: the request sheet or one request's sheet, as the page's state says, or nothing where there is none to open. */
        fun onPrimary() =
            updateReady { ready ->
                when (
                    val tap =
                        titleActionsModel(
                            ready.movie.standard,
                            ready.movie.fourK,
                            ready.movie.requests,
                            ready.movie.mayReport,
                        ).primary.tap
                ) {
                    PrimaryTap.RequestSheet -> ready.copy(requestSheetOpen = true)
                    is PrimaryTap.ManageRequest ->
                        ready.copy(
                            manageRequestId = tap.requestId,
                            manageSheetOpen = true,
                            manageScope = SheetScope.Title,
                            manageFromList = false,
                        )
                    PrimaryTap.StatusSheet -> ready.copy(statusSheetOpen = true)
                }
            }

        /** A request's sheet was asked for from outside the page, as the Requests list does: open it on that request. */
        fun openRequest(id: Int) =
            updateReady {
                it.copy(
                    manageRequestId = id,
                    manageSheetOpen = true,
                    requestsSheetOpen = false,
                    manageFromList = false,
                    manageScope = SheetScope.Both,
                )
            }

        fun pickRequest(id: Int) =
            updateReady {
                it.copy(
                    manageRequestId = id,
                    manageSheetOpen = true,
                    requestsSheetOpen = false,
                    manageFromList = true,
                    manageScope = SheetScope.Request,
                )
            }

        fun backToRequests() =
            updateReady {
                it.copy(
                    manageSheetOpen = true,
                    requestsSheetOpen = false,
                    manageScope = SheetScope.Title,
                    manageRequestId =
                        it.movie.requests
                            .toManage()
                            ?.id ?: it.manageRequestId,
                    manageFromList = false,
                )
            }

        fun backFromRequests() =
            updateReady {
                it.copy(
                    requestsSheetOpen = false,
                    manageSheetOpen = true,
                    manageScope = SheetScope.Title,
                    manageRequestId =
                        it.movie.requests
                            .toManage()
                            ?.id ?: it.manageRequestId,
                    manageFromList = false,
                )
            }

        fun setRequestsSheetOpen(open: Boolean) = updateReady { it.copy(requestsSheetOpen = open) }

        fun setServiceSheetOpen(open: Boolean) = updateReady { it.copy(serviceSheetOpen = open) }

        /** The request's sheet closed. The id stays, and a change made in it reloads the page through the sheet's own `onChanged`. */
        fun closeSheet(sheet: TitleSheet) =
            updateReady {
                when (sheet) {
                    TitleSheet.Manage -> it.copy(manageSheetOpen = false)
                    TitleSheet.Status -> it.copy(statusSheetOpen = false)
                }
            }

        /** The watchlist button was tapped: it flips at once, and flips back with a message if the server refuses. */
        fun toggleWatchlist() {
            val ready = state.value as? TitlePageUiState.Ready ?: return
            if (!ready.movie.mayWatchlist || ready.watchlistBusy) return
            val target = !ready.movie.onWatchlist
            updateReady { it.copy(movie = it.movie.copy(onWatchlist = target), watchlistBusy = true) }
            viewModelScope.launch {
                attempt { repository.setWatchlist(tmdbId, ready.movie.title, target) }.fold(
                    onSuccess = { updateReady { it.copy(watchlistBusy = false) } },
                    onFailure = { failure ->
                        updateReady { it.copy(movie = it.movie.copy(onWatchlist = !target), watchlistBusy = false) }
                        eventFlow.tryEmit(TitlePageEvent.WatchlistFailed(failure.toSeerrError()))
                    },
                )
            }
        }

        /** Where the blocklist stands for this title: the confirm opens and closes, and a block or an unblock is sent. */
        fun blocklist(step: BlocklistStep) {
            when (step) {
                BlocklistStep.Ask -> updateReady { it.copy(blockConfirmOpen = true) }
                BlocklistStep.Cancel -> updateReady { it.copy(blockConfirmOpen = false) }
                BlocklistStep.Block -> {
                    val ready = state.value as? TitlePageUiState.Ready ?: return
                    if (!ready.movie.mayBlock) return
                    updateReady { it.copy(blockConfirmOpen = false) }
                    viewModelScope.sendBlocklistChange(
                        analytics,
                        "movie_blocked",
                        "block_title",
                        repository::serverProfile,
                        send = { repository.block(tmdbId, ready.movie.title) },
                        onDone = {
                            eventFlow.tryEmit(TitlePageEvent.Blocked)
                            reload()
                        },
                        onFailed = { eventFlow.tryEmit(TitlePageEvent.BlockFailed(it)) },
                    )
                }
                BlocklistStep.Unblock -> {
                    if ((state.value as? TitlePageUiState.Ready)?.movie?.mayUnblock != true) return
                    closeSheet(TitleSheet.Status)
                    viewModelScope.sendBlocklistChange(
                        analytics,
                        "movie_unblocked",
                        "unblock_title",
                        repository::serverProfile,
                        send = { repository.unblock(tmdbId) },
                        onDone = {
                            eventFlow.tryEmit(TitlePageEvent.Unblocked)
                            reload()
                        },
                        onFailed = { eventFlow.tryEmit(TitlePageEvent.UnblockFailed(it)) },
                    )
                }
            }
        }

        /** Request in 4K or the remaining seasons, tapped in a sheet: that sheet gives way to the request sheet. */
        fun requestFromSheet(fourK: Boolean) =
            updateReady { it.copy(manageSheetOpen = false, statusSheetOpen = false, requestSheetOpen = true, requestSheetFourK = fourK) }

        fun setReportSheetOpen(open: Boolean) = updateReady { it.copy(reportSheetOpen = open) }

        /** Closing the sheet reads the title again, since a refused send may mean the server's state moved under the page. */
        fun closeRequestSheet() {
            updateReady { it.copy(requestSheetOpen = false, requestSheetFourK = false) }
            reload()
        }

        /** Reads the title again where the page stands: a failure keeps what it shows and says so, rather than dropping to the loading page. */
        fun reload() {
            viewModelScope.launch {
                attempt { repository.movie(tmdbId) }.fold(
                    onSuccess = { movie -> updateReady { it.copy(movie = movie) } },
                    onFailure = { eventFlow.tryEmit(TitlePageEvent.ReloadFailed) },
                )
            }
        }

        /** The server answered a request: the sheet closes and the page reads the title again, so its status moves. */
        fun requested(result: RequestResult) {
            eventFlow.tryEmit(TitlePageEvent.Requested(result))
            closeRequestSheet()
        }

        /** The other films of the film's collection, where it has one; a failed read leaves the rail out, as it does any extra. */
        private fun loadCollection(collectionId: Int) {
            viewModelScope.launch {
                val parts = attempt { repository.collectionParts(collectionId) }.getOrNull() ?: return@launch
                updateReady { it.copy(collectionParts = parts.filterNot { part -> part.tmdbId == tmdbId }) }
            }
        }

        /** Extras that missed the wait fill the rails but not the hero's scores, which would move the copy the page already shows. */
        private fun loadLateExtras(load: Deferred<TitleExtras?>) {
            viewModelScope.launch {
                val late = load.await() ?: return@launch
                updateReady { it.copy(extras = late.copy(ratings = null)) }
            }
        }

        private fun updateReady(transform: (TitlePageUiState.Ready) -> TitlePageUiState.Ready) =
            state.update { (it as? TitlePageUiState.Ready)?.let(transform) ?: it }
    }
