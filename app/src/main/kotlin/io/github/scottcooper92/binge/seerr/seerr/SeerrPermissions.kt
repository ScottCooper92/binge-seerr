package io.github.scottcooper92.binge.seerr.seerr

/** Seerr permission bits (server/lib/permissions.ts); ADMIN implies every permission. */
internal const val PERMISSION_ADMIN = 2
internal const val PERMISSION_MANAGE_SETTINGS = 1 shl 2
internal const val PERMISSION_MANAGE_USERS = 1 shl 3
internal const val PERMISSION_MANAGE_REQUESTS = 1 shl 4
internal const val PERMISSION_REQUEST = 1 shl 5
internal const val PERMISSION_AUTO_APPROVE = 1 shl 7
internal const val PERMISSION_AUTO_APPROVE_MOVIE = 1 shl 8
internal const val PERMISSION_AUTO_APPROVE_TV = 1 shl 9
internal const val PERMISSION_REQUEST_4K = 1 shl 10
internal const val PERMISSION_REQUEST_4K_MOVIE = 1 shl 11
internal const val PERMISSION_REQUEST_4K_TV = 1 shl 12
internal const val PERMISSION_REQUEST_ADVANCED = 1 shl 13
internal const val PERMISSION_REQUEST_VIEW = 1 shl 14
internal const val PERMISSION_AUTO_APPROVE_4K = 1 shl 15
internal const val PERMISSION_AUTO_APPROVE_4K_MOVIE = 1 shl 16
internal const val PERMISSION_AUTO_APPROVE_4K_TV = 1 shl 17
internal const val PERMISSION_REQUEST_MOVIE = 1 shl 18
internal const val PERMISSION_REQUEST_TV = 1 shl 19
internal const val PERMISSION_MANAGE_ISSUES = 1 shl 20
internal const val PERMISSION_VIEW_ISSUES = 1 shl 21
internal const val PERMISSION_CREATE_ISSUES = 1 shl 22
internal const val PERMISSION_AUTO_REQUEST = 1 shl 23
internal const val PERMISSION_AUTO_REQUEST_MOVIE = 1 shl 24
internal const val PERMISSION_AUTO_REQUEST_TV = 1 shl 25
internal const val PERMISSION_RECENT_VIEW = 1 shl 26
internal const val PERMISSION_WATCHLIST_VIEW = 1 shl 27
internal const val PERMISSION_MANAGE_BLOCKLIST = 1 shl 28
internal const val PERMISSION_VIEW_BLOCKLIST = 1 shl 30

/** Whether [bitmask] carries `ADMIN`, which the server reads as every other permission: the one test for it. */
internal fun isAdminBitmask(bitmask: Int): Boolean = bitmask and PERMISSION_ADMIN != 0

/**
 * What the connected user may do, decoded from their permission bitmask. This is what the
 * handshake's capability set is derived from, so a restricted user is offered only what the server
 * would accept; the server's own 401/403 stays the backstop. All-denied for an unresolved user.
 */
data class SeerrPermissions(
    val isAdmin: Boolean = false,
    /** `REQUEST` or `REQUEST_MOVIE`, as Seerr's request route checks a movie request. */
    val canRequestMovie: Boolean = false,
    /** `REQUEST` or `REQUEST_TV`, as it checks a series request. */
    val canRequestSeries: Boolean = false,
    val canRequest4kMovie: Boolean = false,
    val canRequest4kTv: Boolean = false,
    val canRequestAdvanced: Boolean = false,
    val canManageRequests: Boolean = false,
    /** `REQUEST_VIEW`: others' requests; without it or `MANAGE_REQUESTS` the list is the user's own. */
    val canViewRequests: Boolean = false,
    val canManageBlocklist: Boolean = false,
    val canCreateIssues: Boolean = false,
    val canManageIssues: Boolean = false,
    val canViewIssues: Boolean = false,
    val canManageUsers: Boolean = false,
    /**
     * Jellyseerr's own bit; Overseerr's current code never sets it, so there it reads as false unless the user is an
     * admin. There is no flag for `MANAGE_SETTINGS`: the bit exists on the Jellyseerr lineage, and Overseerr up to 1.29
     * defined it too, but no route honours it. The admin settings router needs `ADMIN`, so that is the settings gate (#1004).
     * Only `/settings/public`, `GET /settings/discover` and the Pushover sounds lookup sit outside that router.
     */
    val canViewBlocklist: Boolean = false,
) {
    /** Whether the user may request something; the server checks the media type it is asked for. */
    val canRequest: Boolean get() = canRequestMovie || canRequestSeries

    val canRequest4k: Boolean get() = canRequest4kMovie || canRequest4kTv

    /** Whether some 4K request is open to this user: they hold the permission for a media type the server has 4K on for. */
    fun canRequest4kOn(settings: SeerrPublicSettings): Boolean =
        (canRequest4kMovie && settings.movie4kEnabled) || (canRequest4kTv && settings.series4kEnabled)

    /** The server's issue list admits any of the three; `CREATE_ISSUES` alone sees only the user's own. */
    val canSeeIssues: Boolean get() = canManageIssues || canViewIssues || canCreateIssues

    companion object {
        /**
         * `ADMIN` short-circuits every flag, the `REQUEST_4K` umbrella grants both 4K media types,
         * and a `null` user (a failed `auth/me`) yields all-denied.
         */
        fun fromBits(permissions: Int?): SeerrPermissions {
            val bits = permissions ?: 0
            val isAdmin = isAdminBitmask(bits)

            fun granted(bit: Int) = isAdmin || bits and bit != 0
            val request4k = granted(PERMISSION_REQUEST_4K)
            return SeerrPermissions(
                isAdmin = isAdmin,
                canRequestMovie = granted(PERMISSION_REQUEST) || granted(PERMISSION_REQUEST_MOVIE),
                canRequestSeries = granted(PERMISSION_REQUEST) || granted(PERMISSION_REQUEST_TV),
                canRequest4kMovie = request4k || granted(PERMISSION_REQUEST_4K_MOVIE),
                canRequest4kTv = request4k || granted(PERMISSION_REQUEST_4K_TV),
                canRequestAdvanced = granted(PERMISSION_REQUEST_ADVANCED),
                canManageRequests = granted(PERMISSION_MANAGE_REQUESTS),
                canViewRequests = granted(PERMISSION_REQUEST_VIEW) || granted(PERMISSION_MANAGE_REQUESTS),
                canManageBlocklist = granted(PERMISSION_MANAGE_BLOCKLIST),
                canCreateIssues = granted(PERMISSION_CREATE_ISSUES),
                canManageIssues = granted(PERMISSION_MANAGE_ISSUES),
                canViewIssues = granted(PERMISSION_VIEW_ISSUES),
                canManageUsers = granted(PERMISSION_MANAGE_USERS),
                canViewBlocklist = granted(PERMISSION_VIEW_BLOCKLIST) || granted(PERMISSION_MANAGE_BLOCKLIST),
            )
        }
    }
}

fun SeerrUserDto?.toPermissions(): SeerrPermissions = SeerrPermissions.fromBits(this?.permissions)

/** Every bit that lets a user request something: the umbrellas and each media type's own. */
private val REQUEST_BITS =
    listOf(
        PERMISSION_REQUEST,
        PERMISSION_REQUEST_MOVIE,
        PERMISSION_REQUEST_TV,
        PERMISSION_REQUEST_4K,
        PERMISSION_REQUEST_4K_MOVIE,
        PERMISSION_REQUEST_4K_TV,
    )

/** Each media type's request bits, paired with the auto-approve bits that cover it. */
private val APPROVAL_BY_TYPE =
    listOf(
        listOf(PERMISSION_REQUEST, PERMISSION_REQUEST_MOVIE) to listOf(PERMISSION_AUTO_APPROVE_MOVIE),
        listOf(PERMISSION_REQUEST, PERMISSION_REQUEST_TV) to listOf(PERMISSION_AUTO_APPROVE_TV),
        listOf(PERMISSION_REQUEST_4K, PERMISSION_REQUEST_4K_MOVIE) to listOf(PERMISSION_AUTO_APPROVE_4K, PERMISSION_AUTO_APPROVE_4K_MOVIE),
        listOf(PERMISSION_REQUEST_4K, PERMISSION_REQUEST_4K_TV) to listOf(PERMISSION_AUTO_APPROVE_4K, PERMISSION_AUTO_APPROVE_4K_TV),
    )

/**
 * True when every media type the mask can request has its own auto-approve bit, which is the same as
 * the umbrella for what that default allows. Manage Requests is not counted: it is a separate grant.
 */
private fun approvesEveryRequestableType(granted: (Int) -> Boolean): Boolean =
    APPROVAL_BY_TYPE.filter { (requests, _) -> requests.any(granted) }.all { (_, approvals) -> approvals.any(granted) }

/** What a new user may do, read off the server's `defaultPermissions` bitmask. */
enum class SeerrDefaultAccess {
    NoRequests,
    RequestWithApproval,
    AutoApprove,
    ;

    companion object {
        fun fromBits(bits: Int?): SeerrDefaultAccess {
            val value = bits ?: 0
            val isAdmin = isAdminBitmask(value)

            fun granted(bit: Int) = isAdmin || value and bit != 0
            return when {
                REQUEST_BITS.none(::granted) -> NoRequests
                granted(PERMISSION_AUTO_APPROVE) || approvesEveryRequestableType(::granted) -> AutoApprove
                else -> RequestWithApproval
            }
        }
    }
}
