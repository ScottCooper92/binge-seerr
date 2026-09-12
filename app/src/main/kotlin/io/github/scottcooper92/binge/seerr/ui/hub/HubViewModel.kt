package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The connected hub: the server and account cards, the counts, the downloading strip and the
 * manage rows, over the connection's live health. The overview loads once per connect or re-check
 * and never on the poll; the pending-request badge also refreshes on becoming visible, so returning
 * from a sub-screen picks up an approval without waiting on anything.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HubViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val loader: HubOverviewLoader,
    ) : ViewModel() {
        private val recheckTrigger = MutableStateFlow(0)
        private val isProbing = MutableStateFlow(false)
        private val screenVisible = MutableStateFlow(false)

        private val server: Flow<HubServer?> =
            recheckTrigger.flatMapLatest { flow { emit(runCatching { loader.server() }.getOrNull()) } }

        private val health: Flow<ConnectionHealth> =
            combine(connection.health, isProbing) { health, probing ->
                if (probing) ConnectionHealth.Checking else health.toConnectionHealth()
            }

        private val overview: Flow<HubOverview> =
            recheckTrigger.flatMapLatest {
                flow {
                    emit(HubOverview())
                    emit(loader.load())
                }
            }

        /** A count read on becoming visible, overriding the overview's until the next re-check reloads everything. */
        private val refreshedPendingCount = MutableStateFlow<Int?>(null)

        private val downloadsPoller =
            DownloadsPoller(
                scope = viewModelScope,
                healthy = connection.health.map { it == SeerrConnectionHealth.Healthy },
                fetch = loader::activeDownloads,
            )

        val uiState: StateFlow<HubUiState> =
            combine(
                server,
                health,
                overview,
                downloadsPoller.downloading,
                refreshedPendingCount,
            ) { server, health, overview, downloading, pending ->
                if (server == null) {
                    HubUiState.Loading
                } else {
                    HubUiState.Ready(
                        server = server,
                        health = effectiveHealth(health, overview),
                        overview = overview.copy(pendingRequestCount = pending ?: overview.pendingRequestCount),
                        downloading = downloading,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, HubUiState.Loading)

        private val effectiveHealth: Flow<ConnectionHealth> =
            uiState.map { (it as? HubUiState.Ready)?.health ?: ConnectionHealth.Checking }

        init {
            HubAutoRetry(scope = viewModelScope, health = effectiveHealth, visible = screenVisible, retry = ::recheck)
        }

        fun setScreenVisible(visible: Boolean) {
            screenVisible.value = visible
            downloadsPoller.setScreenVisible(visible)
            if (visible) viewModelScope.launch { loader.pendingRequestCount()?.let { refreshedPendingCount.value = it } }
        }

        /** Re-probes the server and reloads the overview: the "can't reach server" retry. */
        fun recheck() {
            refreshedPendingCount.value = null
            viewModelScope.launch {
                isProbing.value = true
                try {
                    // The probe's outcome reaches health through the cached client's interceptor.
                    runCatching { connection.api().authenticatedUser() }
                } finally {
                    isProbing.value = false
                }
            }
            recheckTrigger.value++
        }

        fun disconnect() {
            viewModelScope.launch { connection.disconnect() }
        }
    }

/**
 * Reconciles the monitor with the overview's `auth/me`: the monitor reaches Unreachable only after a
 * streak, so a lone transient failure would leave it Healthy over an all-denied hub. Only a Healthy
 * reading is overridden, and only once the overview has loaded.
 */
private fun effectiveHealth(
    health: ConnectionHealth,
    overview: HubOverview,
): ConnectionHealth =
    if (health != ConnectionHealth.Healthy || !overview.loaded) {
        health
    } else {
        when (overview.userLoad) {
            HubUserLoad.Rejected -> ConnectionHealth.Unauthorized
            HubUserLoad.Failed -> ConnectionHealth.CouldNotLoad
            HubUserLoad.Loaded, HubUserLoad.Pending -> ConnectionHealth.Healthy
        }
    }

internal fun SeerrConnectionHealth.toConnectionHealth(): ConnectionHealth =
    when (this) {
        SeerrConnectionHealth.Healthy -> ConnectionHealth.Healthy
        SeerrConnectionHealth.Unreachable -> ConnectionHealth.Unreachable
        SeerrConnectionHealth.Unauthorized -> ConnectionHealth.Unauthorized
        // Credentials vanished mid-check; the shell swaps to setup, so this is never rendered.
        SeerrConnectionHealth.NotConnected -> ConnectionHealth.Checking
    }
