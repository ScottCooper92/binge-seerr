package io.github.scottcooper92.binge.seerr.seerr

/**
 * How this app authenticates to a Seerr server.
 *
 * [ApiKey] is the server-wide admin key: it bypasses per-user permission checks, so it acts as
 * full admin. [Session] is a per-user login — the `connect.sid` cookie the `auth/jellyfin` and
 * `auth/local` endpoints set, scoped to that user's permissions, plus their id. Both secrets are
 * stored encrypted at rest.
 */
sealed interface SeerrAuth {
    data class ApiKey(
        val key: String,
    ) : SeerrAuth

    data class Session(
        val cookie: String,
        val userId: Int,
    ) : SeerrAuth
}

/** A per-user login attempt: a Jellyfin/Emby account, or a Seerr-local one. */
sealed interface SeerrLoginRequest {
    data class Jellyfin(
        val username: String,
        val password: String,
    ) : SeerrLoginRequest

    data class Local(
        val email: String,
        val password: String,
    ) : SeerrLoginRequest
}

/**
 * A connection to a Seerr server: where it is, how we authenticate, and which fork it reported
 * itself to be on connect. [variant] is what the host shows as this integration's provider name.
 */
data class SeerrCredentials(
    val baseUrl: String,
    val auth: SeerrAuth,
    val variant: SeerrVariant = SeerrVariant.Unknown,
)

/**
 * Which fork of the Seerr family a server is, from its reported version. Overseerr and Jellyseerr
 * are the legacy forks (both migrating to Seerr on an identical `/api/v1`); [Unknown] is the
 * fallback when the version cannot be read or parsed, and brands as plain "Seerr".
 *
 * [displayName] is a proper noun and is never translated.
 */
enum class SeerrVariant(
    val displayName: String,
) {
    Overseerr("Overseerr"),
    Jellyseerr("Jellyseerr"),
    Seerr("Seerr"),
    Unknown("Seerr"),
    ;

    companion object {
        /**
         * By major version: Overseerr capped at `1.x`, Jellyseerr reached `2.x`, Seerr starts at
         * `3.x`. A missing or non-numeric version — a `develop` build — is [Unknown].
         */
        fun fromVersion(version: String?): SeerrVariant =
            when (
                version
                    ?.trim()
                    ?.removePrefix("v")
                    ?.substringBefore('.')
                    ?.toIntOrNull()
            ) {
                1 -> Overseerr
                2 -> Jellyseerr
                null -> Unknown
                else -> Seerr
            }
    }
}
