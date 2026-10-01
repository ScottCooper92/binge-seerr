package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.BingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.NoBingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealth
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        private val cache: HubOverviewCache,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val pollerTicker: DownloadsPollerTicker = DownloadsPollerTicker(),
        private val installCheck: BingeInstallCheck = NoBingeInstallCheck,
        private val bingeConnection: BingeConnectionStore = NoBingeConnectionStore,
    ) : ViewModel() {
        private val recheckTrigger = MutableStateFlow(0)
        private val isProbing = MutableStateFlow(false)
        private val screenVisible = MutableStateFlow(false)

        /** Re-read on every arrival: installing Binge while this screen is backgrounded should flip the tile unprompted. */
        private val installedTrigger = MutableStateFlow(installCheck.isInstalled())

        /** Bumped by a manual/auto re-check and by the connection itself changing underneath this instance. */
        private val reloadTrigger: Flow<SeerrCredentials?> =
            combine(recheckTrigger, connection.credentials.distinctUntilChanged()) { _, credentials -> credentials }

        /** `flowOn(dispatcher)` per #177/#370: without it, [HubOverviewLoader.server]'s suspend call resumes on Main. */
        private val server: Flow<HubServer?> =
            reloadTrigger
                .flatMapLatest { credentials ->
                    flow {
                        val remembered = cache.serverFor(credentials)
                        remembered?.let { emit(it) }
                        val fresh = runCatching { loader.server() }.getOrNull()
                        fresh?.let { cache.remember(credentials, server = it) }
                        // A failed refresh keeps the remembered server rather than blanking the hub.
                        if (fresh != null || remembered == null) emit(fresh)
                    }
                }.flowOn(dispatcher)

        private val health: Flow<ConnectionHealth> =
            combine(connection.health, isProbing) { health, probing ->
                if (probing) ConnectionHealth.Checking else health.toConnectionHealth()
            }

        /** Same `flowOn(dispatcher)` reason as [server]: [HubOverviewLoader.load] suspends too. */
        private val overview: Flow<HubOverview> =
            reloadTrigger
                .flatMapLatest { credentials ->
                    flow {
                        val remembered = cache.overviewFor(credentials)
                        emit(remembered ?: HubOverview())
                        emit(refreshed(credentials, remembered, loader.load()))
                    }
                }.flowOn(dispatcher)

        /**
         * A good read is remembered. A transient failure keeps the remembered overview's content but
         * carries the failure, so health still reads CouldNotLoad and [HubAutoRetry] keeps retrying
         * under the stale-but-useful hub. A rejected session forgets it: permissions may be gone.
         */
        private fun refreshed(
            credentials: SeerrCredentials?,
            remembered: HubOverview?,
            fresh: HubOverview,
        ): HubOverview =
            when {
                fresh.userLoad == HubUserLoad.Loaded -> fresh.also { cache.remember(credentials, overview = it) }
                fresh.userLoad == HubUserLoad.Failed && remembered != null -> remembered.copy(userLoad = HubUserLoad.Failed)
                else -> fresh.also { cache.forgetOverview(credentials) }
            }

        /** A count read on becoming visible, overriding the overview's until the next re-check reloads everything. */
        private val refreshedPendingCount = MutableStateFlow<Int?>(null)

        private val downloadsPoller =
            DownloadsPoller(
                scope = viewModelScope,
                healthy = connection.health.map { it == SeerrConnectionHealth.Healthy },
                dispatcher = dispatcher,
                fetch = loader::activeDownloads,
                ticker = pollerTicker,
            )

        /** Not installed always wins; otherwise "connected" is whether Binge has ever handshaken this server. */
        private val bingeStatus: Flow<BingeStatus> =
            combine(installedTrigger, bingeConnection.hasConnected) { installed, connected ->
                when {
                    !installed -> BingeStatus.NotInstalled
                    connected -> BingeStatus.Connected
                    else -> BingeStatus.NotConnected
                }
            }

        /** Folded with the downloading strip rather than added as a sixth argument: [combine] has no six-flow overload. */
        private val downloadingAndBingeStatus: Flow<Pair<List<HubDownload>, BingeStatus>> =
            combine(downloadsPoller.downloading, bingeStatus) { downloading, status -> downloading to status }

        val uiState: StateFlow<HubUiState> =
            combine(
                server,
                health,
                overview,
                downloadingAndBingeStatus,
                refreshedPendingCount,
            ) { server, health, overview, (downloading, bingeStatus), pending ->
                // Not loaded is not ready: the overview carries the user's permissions, and every
                // manage row is gated on one, so a Ready built on the placeholder is a hub with
                // Requests alone — a settled-looking menu that then grows rows under a finger.
                if (server == null || !overview.loaded) {
                    HubUiState.Loading
                } else {
                    HubUiState.Ready(
                        server = server,
                        health = effectiveHealth(health, overview),
                        overview = overview.copy(pendingRequestCount = pending ?: overview.pendingRequestCount),
                        downloading = downloading,
                        bingeStatus = bingeStatus,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, HubUiState.Loading)

        private val effectiveHealth: Flow<ConnectionHealth> =
            uiState.map { (it as? HubUiState.Ready)?.health ?: ConnectionHealth.Checking }

        init {
            HubAutoRetry(
                scope = viewModelScope,
                health = effectiveHealth,
                visible = screenVisible,
                dispatcher = dispatcher,
                retry = ::recheck,
            )
        }

        fun setScreenVisible(visible: Boolean) {
            screenVisible.value = visible
            downloadsPoller.setScreenVisible(visible)
            if (visible) {
                installedTrigger.value = installCheck.isInstalled()
                viewModelScope.launch(dispatcher) { loader.pendingRequestCount()?.let { refreshedPendingCount.value = it } }
            }
        }

        /** Re-probes the server and reloads the overview: the "can't reach server" retry. */
        fun recheck() {
            refreshedPendingCount.value = null
            viewModelScope.launch(dispatcher) {
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
            viewModelScope.launch(dispatcher) { connection.disconnect() }
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
        // Unchecked is every cold start: the credentials are saved and the overview has not landed
        // yet. NotConnected means they vanished mid-check, and the shell swaps to setup on it.
        SeerrConnectionHealth.Unchecked, SeerrConnectionHealth.NotConnected -> ConnectionHealth.Checking
    }
