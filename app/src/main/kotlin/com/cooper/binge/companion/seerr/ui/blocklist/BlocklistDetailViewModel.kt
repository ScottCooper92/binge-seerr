package com.cooper.binge.companion.seerr.ui.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.seerr.details
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.CrashBreadcrumbs
import com.cooper.binge.companion.seerr.telemetry.NoOpAnalytics
import com.cooper.binge.companion.seerr.telemetry.NoOpCrashBreadcrumbs
import com.cooper.binge.companion.seerr.telemetry.operationFailed
import com.cooper.binge.companion.seerr.ui.requests.seerrMediaType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * One blocked title's page. [item] and [canManage] arrive from the route (see [BlocklistDetailUiState]),
 * so it fetches only the backdrop and overview, the best-effort lookup that
 * [com.cooper.binge.companion.seerr.ui.requests.RequestDetailViewModel] makes, and the server's web root.
 *
 * It unblocks for itself: the list lives in another navigation entry with its own `ViewModelStore`, so
 * there is nothing of [BlocklistViewModel]'s to call. Its [BlocklistViewModel.setScreenVisible] picks up the removal.
 */
@HiltViewModel(assistedFactory = BlocklistDetailViewModel.Factory::class)
class BlocklistDetailViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val item: BlocklistItem,
        @Assisted canManage: Boolean,
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    ) : ViewModel() {
        private val state = MutableStateFlow(BlocklistDetailUiState(item = item, canManage = canManage))
        val uiState: StateFlow<BlocklistDetailUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<BlocklistDetailEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<BlocklistDetailEvent> = eventFlow.asSharedFlow()

        init {
            viewModelScope.launch(dispatcher) {
                coroutineScope {
                    val api = connection.api()
                    val webRoot = async { attempt { connection.current().baseUrl }.getOrDefault("") }
                    val details = async { attempt { api.details(item.mediaType.seerrMediaType(), item.tmdbId) }.getOrNull() }
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
            crashBreadcrumbs.key("tmdb_id", item.tmdbId.toString())
            crashBreadcrumbs.log("removing from blocklist")
            viewModelScope.launch(dispatcher) {
                attempt {
                    connection.api().removeFromBlocklist(
                        connection.profile().blocklistPath,
                        item.tmdbId,
                        connection.profile().unblockMediaType(item.mediaType.seerrMediaType()),
                    )
                }.onSuccess {
                    analytics.event(AnalyticsEvents.BLOCKLIST_CHANGED, mapOf(AnalyticsEvents.PARAM_ACTION to "removed"))
                    eventFlow.emit(BlocklistDetailEvent.Removed)
                }.onFailure {
                    analytics.operationFailed("unblock_title", it, connection)
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
