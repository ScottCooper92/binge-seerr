package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.details
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.seerr.toTmdbBackdropUrl
import io.github.scottcooper92.binge.seerr.ui.requests.seerrMediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One blocked title's page: [item] and [canManage] arrive from the route already known (see
 * [BlocklistDetailUiState]), so the one thing this fetches for itself is the backdrop and overview —
 * the same best-effort `movieDetails`/`tvDetails` lookup
 * [io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailViewModel] makes for its own hero —
 * plus the server's web root, for the "open elsewhere" action.
 *
 * Unblocking is this screen's own call rather than a delegate back to [BlocklistViewModel]'s: the
 * two live in different navigation entries with independent `ViewModelStore`s, so there is nothing
 * of the list's to call into. [BlocklistViewModel.setScreenVisible] is what picks up a removal made
 * here once the browser is back on screen.
 */
@HiltViewModel(assistedFactory = BlocklistDetailViewModel.Factory::class)
class BlocklistDetailViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val item: BlocklistItem,
        @Assisted canManage: Boolean,
    ) : ViewModel() {
        private val state = MutableStateFlow(BlocklistDetailUiState(item = item, canManage = canManage))
        val uiState: StateFlow<BlocklistDetailUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<BlocklistDetailEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<BlocklistDetailEvent> = eventFlow.asSharedFlow()

        init {
            viewModelScope.launch(dispatcher) {
                coroutineScope {
                    val api = connection.api()
                    val webRoot = async { runCatching { connection.current().baseUrl }.getOrDefault("") }
                    val details = async { runCatching { api.details(item.mediaType.seerrMediaType(), item.tmdbId) }.getOrNull() }
                    val resolvedDetails = details.await()
                    state.update {
                        it.copy(
                            backdropUrl = resolvedDetails?.backdropPath?.toTmdbBackdropUrl(),
                            overview = resolvedDetails?.overview?.takeIf { overview -> overview.isNotBlank() },
                            webUrl = webRoot.await() + item.mediaType.seerrMediaType() + "/" + item.tmdbId,
                        )
                    }
                }
            }
        }

        fun unblock() {
            if (state.value.unblocking) return
            state.update { it.copy(unblocking = true) }
            viewModelScope.launch(dispatcher) {
                runCatching {
                    connection.api().removeFromBlocklist(
                        connection.profile().blocklistPath,
                        item.tmdbId,
                        item.mediaType.seerrMediaType(),
                    )
                }.onSuccess {
                    eventFlow.emit(BlocklistDetailEvent.Removed)
                }.onFailure {
                    state.update { current -> current.copy(unblocking = false) }
                    eventFlow.emit(BlocklistDetailEvent.Failed(it.toSeerrError()))
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(
                item: BlocklistItem,
                canManage: Boolean,
            ): BlocklistDetailViewModel
        }
    }
