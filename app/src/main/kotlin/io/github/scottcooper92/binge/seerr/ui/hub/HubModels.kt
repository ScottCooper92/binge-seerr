package io.github.scottcooper92.binge.seerr.ui.hub

import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.users.settings.UserRole
import io.github.scottcooper92.binge.seerr.ui.users.userRole

/**
 * Live reachability of the connected server as the hub shows it. [Checking] is the brief re-probe;
 * [CouldNotLoad] is a server that answered but whose `auth/me` failed transiently, retryable in
 * place and distinct from a session the server rejected, [Unauthorized]. [LocalNetworkDenied] is an
 * unreachable server on the user's own network while Android refuses this app that network: the
 * permission, not the server, so the way out is Settings rather than a retry.
 */
enum class ConnectionHealth { Checking, Healthy, Unreachable, LocalNetworkDenied, CouldNotLoad, Unauthorized }

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
) {
    val role: UserRole get() = userRole(id, isAdmin)
}

/** A per-type request quota; a null bucket is unlimited. */
data class HubQuota(
    val movie: HubQuotaBucket?,
    val tv: HubQuotaBucket?,
) {
    /** The metered bucket nearest its limit, the one the hub's ring speaks for. Null when neither is metered. */
    fun tightest(): TypedQuotaBucket? =
        listOfNotNull(
            movie?.let { TypedQuotaBucket(HubQuotaType.Movie, it) },
            tv?.let { TypedQuotaBucket(HubQuotaType.Tv, it) },
        ).maxByOrNull { it.bucket.usedFraction }
}

enum class HubQuotaType { Movie, Tv }

/** A quota bucket and which type of request it meters. */
data class TypedQuotaBucket(
    val type: HubQuotaType,
    val bucket: HubQuotaBucket,
)

data class HubQuotaBucket(
    val limit: Int,
    val remaining: Int,
    val days: Int?,
) {
    val used: Int get() = (limit - remaining).coerceIn(0, limit)

    /** [used] as a fraction of [limit]. A limit of none left to give reads as spent. */
    val usedFraction: Float get() = if (limit <= 0) 1f else used.toFloat() / limit
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

/** Binge's relationship to this device, for the Hub's contextual tile (#469). */
enum class BingeStatus { NotInstalled, NotConnected, Connected }

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

    /**
     * The first read of the server failed and nothing is remembered to show instead, which is every
     * cold start against a server that does not answer. [health] is the problem the screen names;
     * it is the same way out as a [Ready] hub whose health [isProblem].
     */
    data class Error(
        val health: ConnectionHealth,
        /** A retry started from this problem is in flight; the problem stays until the server answers (#873). */
        val rechecking: Boolean = false,
    ) : HubUiState

    data class Ready(
        val server: HubServer,
        val health: ConnectionHealth,
        val overview: HubOverview,
        val downloading: List<HubDownload>,
        val bingeStatus: BingeStatus,
        /** The user closed the hint [bingeStatus] shows, so the tile leaves it out. */
        val bingeHintDismissed: Boolean = false,
        /**
         * A retry started from a problem [health] is in flight. The problem stays named until the server has answered,
         * so the dashboard isn't shown on a re-probe's way through Checking, then taken away again (#873).
         */
        val rechecking: Boolean = false,
        /** A pull's re-read of the dashboard is running, which the pull's spinner shows. */
        val refreshing: Boolean = false,
    ) : HubUiState
}
