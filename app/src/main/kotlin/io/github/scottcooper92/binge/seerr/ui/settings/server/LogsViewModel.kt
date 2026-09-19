package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val REFRESH_MILLIS = 10_000L

/** Internal so the test measures the debounce this file sets rather than a copy of the number. */
internal const val SEARCH_DEBOUNCE_MS = 300L

/**
 * The logs page: one cached paged list per level over the shared search, paged from the top; while
 * the selected level's list is at the top it is re-read on an interval, so the newest lines arrive
 * on their own.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LogsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val state = MutableStateFlow(LogsUiState())
        val uiState: StateFlow<LogsUiState> = state.asStateFlow()

        private val following = MutableStateFlow(false)

        internal var refreshMillis = REFRESH_MILLIS

        /** A blank query needs no debounce, so the first page is not held back. */
        private val query: Flow<String> =
            state.map { it.search }.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }.distinctUntilChanged()

        private val streams: Map<LogLevel, Flow<PagingData<LogEntry>>> =
            LogLevel.entries.associateWith { level ->
                query
                    .flatMapLatest { search ->
                        Pager(config = PagingConfig(pageSize = LOGS_PAGE_SIZE)) {
                            LogsPagingSource(api = connection::api, level = level, search = search)
                        }.flow
                    }.cachedIn(viewModelScope)
            }

        fun entries(level: LogLevel): Flow<PagingData<LogEntry>> = streams.getValue(level)

        private val eventFlow = MutableSharedFlow<LogsEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<LogsEvent> = eventFlow.asSharedFlow()

        init {
            // collectLatest, so the wait is cancelled the moment following stops rather than
            // firing one more time on the interval already under way.
            viewModelScope.launch(dispatcher) {
                following.collectLatest { on ->
                    while (on) {
                        delay(refreshMillis)
                        eventFlow.emit(LogsEvent.Refresh)
                    }
                }
            }
        }

        fun setLevel(level: LogLevel) = state.update { it.copy(level = level) }

        fun setSearch(search: String) = state.update { it.copy(search = search) }

        fun setFollowing(on: Boolean) {
            following.value = on
        }
    }
