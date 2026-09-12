package io.github.scottcooper92.binge.seerr.seerr

/** Seerr permission bits (server/lib/permissions.ts); ADMIN implies every permission. */
private const val PERMISSION_ADMIN = 2
private const val PERMISSION_MANAGE_REQUESTS = 1 shl 4
private const val PERMISSION_REQUEST = 1 shl 5
private const val PERMISSION_REQUEST_4K = 1 shl 10
private const val PERMISSION_REQUEST_4K_MOVIE = 1 shl 11
private const val PERMISSION_REQUEST_4K_TV = 1 shl 12
private const val PERMISSION_REQUEST_ADVANCED = 1 shl 13
private const val PERMISSION_CREATE_ISSUES = 1 shl 22
private const val PERMISSION_MANAGE_BLOCKLIST = 1 shl 28

/**
 * What the connected user may do, decoded from their permission bitmask. This is what the
 * handshake's capability set is derived from, so a restricted user is offered only what the server
 * would accept; the server's own 401/403 stays the backstop. All-denied for an unresolved user.
 */
data class SeerrPermissions(
    val canRequest: Boolean = false,
    val canRequest4kMovie: Boolean = false,
    val canRequest4kTv: Boolean = false,
    val canRequestAdvanced: Boolean = false,
    val canManageRequests: Boolean = false,
    val canManageBlocklist: Boolean = false,
    val canCreateIssues: Boolean = false,
) {
    val canRequest4k: Boolean get() = canRequest4kMovie || canRequest4kTv

    companion object {
        /**
         * `ADMIN` short-circuits every flag, the `REQUEST_4K` umbrella grants both 4K media types,
         * and a `null` user (a failed `auth/me`) yields all-denied.
         */
        fun fromBits(permissions: Int?): SeerrPermissions {
            val bits = permissions ?: 0
            val isAdmin = bits and PERMISSION_ADMIN != 0

            fun granted(bit: Int) = isAdmin || bits and bit != 0
            val request4k = granted(PERMISSION_REQUEST_4K)
            return SeerrPermissions(
                canRequest = granted(PERMISSION_REQUEST),
                canRequest4kMovie = request4k || granted(PERMISSION_REQUEST_4K_MOVIE),
                canRequest4kTv = request4k || granted(PERMISSION_REQUEST_4K_TV),
                canRequestAdvanced = granted(PERMISSION_REQUEST_ADVANCED),
                canManageRequests = granted(PERMISSION_MANAGE_REQUESTS),
                canManageBlocklist = granted(PERMISSION_MANAGE_BLOCKLIST),
                canCreateIssues = granted(PERMISSION_CREATE_ISSUES),
            )
        }
    }
}

fun SeerrUserDto?.toPermissions(): SeerrPermissions = SeerrPermissions.fromBits(this?.permissions)
