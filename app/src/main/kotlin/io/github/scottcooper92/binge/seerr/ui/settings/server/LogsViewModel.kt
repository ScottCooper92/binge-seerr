package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val REFRESH_MILLIS = 10_000L

/**
 * The logs page: the level and the search are the query, paged from the top; while the list is
 * at the top it is re-read on an interval, so the newest lines arrive on their own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ViewModel() {
        private val state = MutableStateFlow(LogsUiState())
        val uiState: StateFlow<LogsUiState> = state.asStateFlow()

        private val following = MutableStateFlow(false)

        internal var refreshMillis = REFRESH_MILLIS

        val entries: Flow<PagingData<LogEntry>> =
            state
                .flatMapLatest { query ->
                    Pager(config = PagingConfig(pageSize = LOGS_PAGE_SIZE)) {
                        LogsPagingSource(api = connection::api, level = query.level, search = query.search)
                    }.flow
                }.cachedIn(viewModelScope)

        /** Ticks while the list sits at the top; the page refreshes on each. */
        val refreshTicks: Flow<Unit> =
            following.flatMapLatest { on ->
                flow {
                    while (on) {
                        delay(refreshMillis)
                        emit(Unit)
                    }
                }
            }

        fun setLevel(level: LogLevel) = state.update { it.copy(level = level) }

        fun setSearch(search: String) = state.update { it.copy(search = search) }

        fun setFollowing(on: Boolean) {
            following.value = on
        }
    }
