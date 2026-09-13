package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject

/** The cache page: the API caches, the image caches and, on Seerr 3, the DNS cache; a flush re-reads the counts. */
@HiltViewModel
class CacheViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ViewModel() {
        private val state = MutableStateFlow<CacheUiState>(CacheUiState.Loading)
        val uiState: StateFlow<CacheUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

        init {
            reload()
        }

        fun reload() {
            state.value = CacheUiState.Loading
            viewModelScope.launch { state.value = read() }
        }

        fun flush(cacheId: String) = flushing(cacheId) { api -> api.flushCache(cacheId) }

        fun flushDnsEntry(hostname: String) = flushing("dns:$hostname") { api -> api.flushDnsEntry(hostname) }

        private fun flushing(
            key: String,
            call: suspend (io.github.scottcooper92.binge.seerr.seerr.SeerrApi) -> Response<Unit>,
        ) {
            val ready = state.value as? CacheUiState.Ready ?: return
            if (key in ready.busyIds) return
            state.value = ready.copy(busyIds = ready.busyIds + key)
            viewModelScope.launch {
                runCatching {
                    val response = call(connection.api())
                    if (!response.isSuccessful) throw HttpException(response)
                }.onSuccess {
                    val reread = read()
                    state.value = if (reread is CacheUiState.Ready) reread.copy(busyIds = busy() - key) else reread
                    eventFlow.emit(EditorEvent.Notice(R.string.server_settings_cache_flushed))
                }.onFailure { failure ->
                    state.update { current -> (current as? CacheUiState.Ready)?.copy(busyIds = current.busyIds - key) ?: current }
                    eventFlow.emit(EditorEvent.Failed(failure.toSeerrError()))
                }
            }
        }

        private fun busy(): Set<String> = (state.value as? CacheUiState.Ready)?.busyIds.orEmpty()

        private suspend fun read(): CacheUiState =
            runCatching { connection.api().caches().toReady() }
                .getOrElse { CacheUiState.Error(it.toSeerrError()) }
    }
