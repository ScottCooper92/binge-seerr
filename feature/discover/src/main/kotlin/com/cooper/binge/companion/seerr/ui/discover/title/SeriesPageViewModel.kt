package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.SeriesDetailsRepository
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
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

/** One season's episodes as the page holds them: read once when the season is first selected, and again only after a failure. */
sealed interface EpisodesState {
    data object Loading : EpisodesState

    data class Ready(
        val episodes: List<SeasonEpisode>,
    ) : EpisodesState

    data class Error(
        val error: SeerrError,
    ) : EpisodesState
}

sealed interface SeriesPageUiState {
    data object Loading : SeriesPageUiState

    data class Error(
        val error: SeerrError,
    ) : SeriesPageUiState

    /**
     * [extras] is null until the sections that load apart have arrived, and a section that failed is simply empty in it.
     * [selectedSeason] is the season whose episodes show, and [episodes] holds what has been read of each season.
     */
    data class Ready(
        val series: SeriesDetails,
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
        val selectedSeason: Int? = null,
        val episodes: Map<Int, EpisodesState> = emptyMap(),
    ) : SeriesPageUiState {
        /** The Request button shows while either version still has a season to ask for. */
        val mayRequest: Boolean get() = series.standard.mayRequest || series.fourK?.mayRequest == true
    }
}

sealed interface SeriesPageEvent {
    /** A request went through, for the screen's snackbar. */
    data class Requested(
        val result: RequestResult,
    ) : SeriesPageEvent

    /** The read after a request failed: the page keeps what it had, and the screen offers to try again. */
    data object ReloadFailed : SeriesPageEvent

    /** The title came off the blocklist, for the screen's snackbar; the page reads it again. */
    data object Unblocked : SeriesPageEvent

    /** The show was blocked, for the screen's snackbar; the page reads it again. */
    data object Blocked : SeriesPageEvent

    /** The server refused or could not be reached, so the show is not blocked. */
    data class BlockFailed(
        val error: SeerrError,
    ) : SeriesPageEvent

    /** The watchlist call failed, so the button is back where it was; the screen says so. */
    data class WatchlistFailed(
        val error: SeerrError,
    ) : SeriesPageEvent

    /** The server refused or could not be reached, so the title stays blocklisted. */
    data class UnblockFailed(
        val error: SeerrError,
    ) : SeriesPageEvent
}

/** The series page. Detail keyed on an id, so it shares [Lazily][kotlinx.coroutines.flow.SharingStarted.Lazily] with the other detail pages: it loads once. */
@HiltViewModel(assistedFactory = SeriesPageViewModel.Factory::class)
class SeriesPageViewModel
    @AssistedInject
    constructor(
        private val repository: SeriesDetailsRepository,
        @Assisted private val tmdbId: Int,
        private val analytics: Analytics = NoOpAnalytics,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(tmdbId: Int): SeriesPageViewModel
        }

        private val state = MutableStateFlow<SeriesPageUiState>(SeriesPageUiState.Loading)
        val uiState: StateFlow<SeriesPageUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<SeriesPageEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<SeriesPageEvent> = eventFlow.asSharedFlow()

        init {
            load()
        }

        fun load() {
            state.value = SeriesPageUiState.Loading
            viewModelScope.launch {
                attempt { repository.series(tmdbId) }.fold(
                    onSuccess = { series ->
                        val first = (series.seasons.firstOrNull { it.number > 0 } ?: series.seasons.firstOrNull())?.number
                        val extrasLoad = async { attempt { repository.extras(tmdbId) }.getOrNull() }
                        val extras = withTimeoutOrNull(HERO_SETTLE_MS) { extrasLoad.await() }
                        state.value = SeriesPageUiState.Ready(series, selectedSeason = first, extras = extras)
                        if (extras == null) loadLateExtras(extrasLoad)
                        first?.let(::loadEpisodes)
                    },
                    onFailure = { state.value = SeriesPageUiState.Error(it.toSeerrError()) },
                )
            }
        }

        /** Shows a season's episodes, reading them the first time. */
        fun selectSeason(number: Int) {
            val current = ready ?: return
            if (current.series.seasons.none { it.number == number }) return
            updateReady { it.copy(selectedSeason = number) }
            loadEpisodes(number)
        }

        fun retryEpisodes() {
            ready?.selectedSeason?.let(::loadEpisodes)
        }

        /** The primary was tapped: the request sheet or one request's sheet, as the page's state says, or nothing where there is none to open. */
        fun onPrimary() =
            updateReady { ready ->
                when (
                    val tap =
                        titleActionsModel(
                            ready.series.standard,
                            ready.series.fourK,
                            ready.series.requests,
                            ready.series.mayReport,
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
            val ready = state.value as? SeriesPageUiState.Ready ?: return
            if (!ready.series.mayWatchlist || ready.watchlistBusy) return
            val target = !ready.series.onWatchlist
            updateReady { it.copy(series = it.series.copy(onWatchlist = target), watchlistBusy = true) }
            viewModelScope.launch {
                attempt { repository.setWatchlist(tmdbId, ready.series.name, target) }.fold(
                    onSuccess = {
                        analytics.event(
                            AnalyticsEvents.WATCHLIST_CHANGED,
                            mapOf(
                                AnalyticsEvents.PARAM_ACTION to if (target) "added" else "removed",
                                AnalyticsEvents.PARAM_TYPE to "series",
                            ),
                        )
                        updateReady { it.copy(watchlistBusy = false) }
                    },
                    onFailure = { failure ->
                        updateReady { it.copy(series = it.series.copy(onWatchlist = !target), watchlistBusy = false) }
                        eventFlow.tryEmit(SeriesPageEvent.WatchlistFailed(failure.toSeerrError()))
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
                    val ready = ready ?: return
                    if (!ready.series.mayBlock) return
                    updateReady { it.copy(blockConfirmOpen = false) }
                    viewModelScope.sendBlocklistChange(
                        analytics,
                        "series_blocked",
                        "block_title",
                        repository::serverProfile,
                        send = { repository.block(tmdbId, ready.series.name) },
                        onDone = {
                            eventFlow.tryEmit(SeriesPageEvent.Blocked)
                            reload()
                        },
                        onFailed = { eventFlow.tryEmit(SeriesPageEvent.BlockFailed(it)) },
                    )
                }
                BlocklistStep.Unblock -> {
                    if (ready?.series?.mayUnblock != true) return
                    closeSheet(TitleSheet.Status)
                    viewModelScope.sendBlocklistChange(
                        analytics,
                        "series_unblocked",
                        "unblock_title",
                        repository::serverProfile,
                        send = { repository.unblock(tmdbId) },
                        onDone = {
                            eventFlow.tryEmit(SeriesPageEvent.Unblocked)
                            reload()
                        },
                        onFailed = { eventFlow.tryEmit(SeriesPageEvent.UnblockFailed(it)) },
                    )
                }
            }
        }

        /** Request in 4K or the remaining seasons, tapped in a sheet: that sheet gives way to the request sheet. */
        fun requestFromSheet(fourK: Boolean) =
            updateReady { it.copy(manageSheetOpen = false, statusSheetOpen = false, requestSheetOpen = true, requestSheetFourK = fourK) }

        fun setReportSheetOpen(open: Boolean) = updateReady { it.copy(reportSheetOpen = open) }

        /** Closing the sheet reads the show again, since a request moves its seasons and a refused one may mean the server's state moved. */
        fun closeRequestSheet() {
            updateReady { it.copy(requestSheetOpen = false, requestSheetFourK = false) }
            reload()
        }

        /** Reads the show again where the page stands: a failure keeps what it shows and says so, rather than dropping to the loading page. */
        fun reload() {
            viewModelScope.launch {
                attempt { repository.series(tmdbId) }.fold(
                    onSuccess = { series -> updateReady { it.copy(series = series) } },
                    onFailure = { eventFlow.tryEmit(SeriesPageEvent.ReloadFailed) },
                )
            }
        }

        /** The server answered a request: the sheet closes and the page reads the show again, so its seasons move. */
        fun requested(result: RequestResult) {
            eventFlow.tryEmit(SeriesPageEvent.Requested(result))
            closeRequestSheet()
        }

        private fun loadEpisodes(number: Int) {
            if (ready?.episodes?.get(number) is EpisodesState.Loading || ready?.episodes?.get(number) is EpisodesState.Ready) return
            updateReady { it.copy(episodes = it.episodes + (number to EpisodesState.Loading)) }
            viewModelScope.launch {
                val result =
                    attempt { repository.season(tmdbId, number) }.fold(
                        onSuccess = { EpisodesState.Ready(it) },
                        onFailure = { EpisodesState.Error(it.toSeerrError()) },
                    )
                updateReady { it.copy(episodes = it.episodes + (number to result)) }
            }
        }

        private val ready: SeriesPageUiState.Ready? get() = state.value as? SeriesPageUiState.Ready

        /** Extras that missed the wait fill the rails but not the hero's scores, which would move the copy the page already shows. */
        private fun loadLateExtras(load: Deferred<TitleExtras?>) {
            viewModelScope.launch {
                val late = load.await() ?: return@launch
                updateReady { it.copy(extras = late.copy(ratings = null)) }
            }
        }

        fun updateReady(transform: (SeriesPageUiState.Ready) -> SeriesPageUiState.Ready) =
            state.update { (it as? SeriesPageUiState.Ready)?.let(transform) ?: it }
    }
