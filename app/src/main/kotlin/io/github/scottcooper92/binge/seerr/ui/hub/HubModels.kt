package io.github.scottcooper92.binge.seerr.ui.hub

import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant

/**
 * Live reachability of the connected server as the hub shows it. [Checking] is the brief re-probe;
 * [CouldNotLoad] is a server that answered but whose `auth/me` failed transiently, retryable in
 * place and distinct from a session the server rejected, [Unauthorized].
 */
enum class ConnectionHealth { Checking, Healthy, Unreachable, CouldNotLoad, Unauthorized }

/** The outcome of the overview's `auth/me`, kept apart from its data so a failed load is never read as a restricted user. */
enum class HubUserLoad { Pending, Loaded, Failed, Rejected }

/** The connected server as the hero card names it. */
data class HubServer(
    val baseUrl: String,
    val title: String,
    val variant: SeerrVariant,
    val versionLabel: String?,
    val updateAvailable: Boolean,
    val commitsBehind: Int,
)

/** The connected user for the account card: their name, whether they administer the server, and their quota. */
data class HubAccount(
    val id: Int,
    val name: String,
    val isAdmin: Boolean,
    val avatarUrl: String?,
)

/** A per-type request quota; a null bucket is unlimited. */
data class HubQuota(
    val movie: HubQuotaBucket?,
    val tv: HubQuotaBucket?,
)

data class HubQuotaBucket(
    val limit: Int,
    val remaining: Int,
    val days: Int?,
) {
    val used: Int get() = (limit - remaining).coerceIn(0, limit)
}

/**
 * The hero card's numbers and the manage rows' badges, fetched once per connect or re-check and
 * never on the poll. Every field degrades to absent on its own failure; only [userLoad] is judged.
 */
data class HubOverview(
    val loaded: Boolean = false,
    val userLoad: HubUserLoad = HubUserLoad.Pending,
    val account: HubAccount? = null,
    val quota: HubQuota? = null,
    val permissions: SeerrPermissions = SeerrPermissions(),
    val movieRequestCount: Int? = null,
    val tvRequestCount: Int? = null,
    val pendingRequestCount: Int? = null,
    val openIssueCount: Int? = null,
    val userCount: Int? = null,
    val blocklistCount: Int? = null,
    /** From the profile: whether the lineage has these at all, before permissions are asked. */
    val hasIssues: Boolean = false,
    val hasBlocklist: Boolean = false,
)

/** One card of the "Downloading now" strip. */
data class HubDownload(
    val requestId: Int,
    val title: String?,
    val posterUrl: String?,
    val fraction: Float,
    val totalBytes: Long?,
    val etaMinutes: Int?,
)

sealed interface HubUiState {
    data object Loading : HubUiState

    data class Ready(
        val server: HubServer,
        val health: ConnectionHealth,
        val overview: HubOverview,
        val downloading: List<HubDownload>,
    ) : HubUiState
}
