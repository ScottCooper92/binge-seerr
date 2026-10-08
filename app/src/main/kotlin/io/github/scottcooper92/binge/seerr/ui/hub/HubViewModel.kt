package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.BingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.BingeHint
import io.github.scottcooper92.binge.seerr.auth.NoBingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealth
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.LocalNetworkPermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.isBlockedByLocalNetwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
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
        private val localNetwork: LocalNetworkPermission = LocalNetworkPermission.AlwaysGranted,
    ) : ViewModel() {
        private val recheckTrigger = MutableStateFlow(0)
        private val isProbing = MutableStateFlow(false)
        private val screenVisible = MutableStateFlow(false)

        /** Re-read on every arrival: installing Binge while this screen is backgrounded should flip the tile unprompted. */
        private val installedTrigger = MutableStateFlow(installCheck.isInstalled())

        /** Bumped by a manual/auto re-check and by the connection itself changing underneath this instance. */
        private val reloadTrigger: Flow<SeerrCredentials?> =
            combine(recheckTrigger, connection.credentials.distinctUntilChanged()) { _, credentials -> credentials }

        /**
         * `flowOn(dispatcher)` per #177/#370: without it, [HubOverviewLoader.server]'s suspend call resumes on Main.
         * Failure is its own value rather than null, because a null cannot say "still reading" from "gave up".
         */
        private val server: Flow<ServerRead> =
            reloadTrigger
                .flatMapLatest { credentials ->
                    flow {
                        val remembered = cache.serverFor(credentials)
                        emit(remembered?.let { ServerRead.Loaded(it) } ?: ServerRead.Pending)
                        val fresh =
                            runCatching { loader.server() }
                                .onFailure { if (it is CancellationException) throw it }
                                .getOrNull()
                        fresh?.let { cache.remember(credentials, server = it) }
                        // A failed refresh keeps the remembered server rather than blanking the hub.
                        if (fresh != null) {
                            emit(ServerRead.Loaded(fresh))
                        } else if (remembered == null) {
                            emit(ServerRead.Failed)
                        }
                    }
                }.flowOn(dispatcher)

        private val health: Flow<ConnectionHealth> =
            combine(connection.health, isProbing, connection.credentials) { health, probing, credentials ->
                if (probing) {
                    ConnectionHealth.Checking
                } else {
                    health.toConnectionHealth().orLocalNetworkDenied(credentials?.baseUrl, localNetwork)
                }
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

        private val bingeHintDismissed: Flow<Boolean> =
            combine(bingeStatus, bingeConnection.dismissedHints) { status, dismissed -> status.hint() in dismissed }

        /** Folded with the downloading strip rather than added as a sixth argument: [combine] has no six-flow overload. */
        private val downloadingAndBingeStatus: Flow<Triple<List<HubDownload>, BingeStatus, Boolean>> =
            combine(downloadsPoller.downloading, bingeStatus, bingeHintDismissed) { downloading, status, dismissed ->
                Triple(downloading, status, dismissed)
            }

        private val freshState: Flow<HubUiState> =
            combine(
                server,
                health,
                overview,
                downloadingAndBingeStatus,
                refreshedPendingCount,
            ) { server, health, overview, (downloading, bingeStatus, hintDismissed), pending ->
                // Not loaded is not ready: the overview carries the user's permissions, and every
                // manage row is gated on one, so a Ready built on the placeholder is a hub with
                // Requests alone — a settled-looking menu that then grows rows under a finger.
                when {
                    server is ServerRead.Failed ->
                        HubUiState.Error(health.takeIf { it == ConnectionHealth.LocalNetworkDenied } ?: ConnectionHealth.Unreachable)
                    server !is ServerRead.Loaded || !overview.loaded -> HubUiState.Loading
                    else ->
                        HubUiState.Ready(
                            server = server.server,
                            health = effectiveHealth(health, overview),
                            overview = overview.copy(pendingRequestCount = pending ?: overview.pendingRequestCount),
                            downloading = downloading,
                            bingeStatus = bingeStatus,
                            bingeHintDismissed = hintDismissed,
                        )
                }
            }

        /** [freshState], with a problem held through the re-check that is meant to clear it: see [holdingProblem]. */
        val uiState: StateFlow<HubUiState> =
            freshState
                .scan<HubUiState, HubUiState>(HubUiState.Loading) { shown, fresh -> holdingProblem(shown, fresh) }
                .stateIn(viewModelScope, SharingStarted.Lazily, HubUiState.Loading)

        private val effectiveHealth: Flow<ConnectionHealth> =
            uiState.map {
                when (it) {
                    is HubUiState.Ready -> it.health
                    is HubUiState.Error -> it.health
                    HubUiState.Loading -> ConnectionHealth.Checking
                }
            }

        init {
            HubAutoRetry(
                scope = viewModelScope,
                health = effectiveHealth,
                visible = screenVisible,
                dispatcher = dispatcher,
                retry = ::recheck,
            )
        }

        /**
         * Whether a screen that reads the hub is showing. Visible, the hub auto-retries a server that is not answering.
         * [dashboard] is a screen that reads the pending count and whether Binge is installed: it re-reads both. A screen
         * that reads only the health or the account passes false, so it doesn't fetch what it never draws (#827).
         * [downloads] is whether it also draws the downloads strip, which polls; only the phone's dashboard does, so TV
         * Settings, which reads the count and the install state but no downloads, passes false (#837).
         */
        fun setScreenVisible(
            visible: Boolean,
            dashboard: Boolean = true,
            downloads: Boolean = dashboard,
        ) {
            screenVisible.value = visible
            downloadsPoller.setScreenVisible(visible && downloads)
            if (visible && dashboard) {
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

        /** Closes the hint the tile is showing; the other state's hint, if the user reaches it later, is still shown. */
        fun dismissBingeHint() {
            viewModelScope.launch(dispatcher) { bingeConnection.dismissHint(bingeStatus.first().hint()) }
        }

        fun disconnect() {
            viewModelScope.launch(dispatcher) { connection.disconnect() }
        }
    }

/** What the first read of the server has said so far. */
private sealed interface ServerRead {
    data object Pending : ServerRead

    data class Loaded(
        val server: HubServer,
    ) : ServerRead

    data object Failed : ServerRead
}

private fun BingeStatus.hint(): BingeHint =
    when (this) {
        BingeStatus.NotInstalled -> BingeHint.NotInstalled
        BingeStatus.NotConnected -> BingeHint.NotConnected
        BingeStatus.Connected -> BingeHint.Connected
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

/**
 * An unreachable server on the user's own network, while the platform refuses this app that network,
 * is the permission and not the server. Judged when the health is read, so granting it in Settings and
 * retrying clears it; any other health, and any public server, is left as it was.
 */
internal fun ConnectionHealth.orLocalNetworkDenied(
    baseUrl: String?,
    permission: LocalNetworkPermission,
): ConnectionHealth =
    if (this == ConnectionHealth.Unreachable && baseUrl?.isBlockedByLocalNetwork(permission) == true) {
        ConnectionHealth.LocalNetworkDenied
    } else {
        this
    }

/**
 * What the hub shows next, given what it [shown] and the [fresh] state. A re-check passes through Checking, and on its
 * way the server and the overview re-emit what is remembered: shown as is, that is the dashboard, let back in before
 * anything has answered, then taken away again. So while the last settled state named a problem, a Checking or Loading
 * state keeps naming it, marked rechecking, until the re-check settles on a health of its own (#873). A cold start has no
 * earlier problem, so it still shows the remembered dashboard while it checks.
 */
internal fun holdingProblem(
    shown: HubUiState,
    fresh: HubUiState,
): HubUiState {
    val held =
        when (shown) {
            is HubUiState.Error -> shown.health
            is HubUiState.Ready -> shown.health.takeIf { it.isProblem() }
            HubUiState.Loading -> null
        } ?: return fresh
    return when (fresh) {
        is HubUiState.Ready -> if (fresh.health == ConnectionHealth.Checking) fresh.copy(health = held, rechecking = true) else fresh
        HubUiState.Loading ->
            when (shown) {
                is HubUiState.Error -> shown.copy(rechecking = true)
                is HubUiState.Ready -> shown.copy(rechecking = true)
                HubUiState.Loading -> fresh
            }
        is HubUiState.Error -> fresh
    }
}
