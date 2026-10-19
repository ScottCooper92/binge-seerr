package com.cooper.binge.companion.seerr.handoff

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import javax.inject.Inject

/** The connected server's Application URL, normalised, or null where the administrator left it blank. */
fun interface ApplicationUrlReader {
    suspend fun read(): String?
}

/**
 * The connected server's own name for itself ([com.cooper.binge.companion.seerr.seerr.SeerrServerProfile.instanceId]), or
 * null where it sends none. It names a server across addresses and is not a secret: anyone who can reach the server can
 * read it, so it never authorises anything alone.
 */
fun interface ServerIdReader {
    suspend fun read(): String?
}

internal class ProfileServerIdReader
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ServerIdReader {
        override suspend fun read(): String? = attempt { connection.profile() }.getOrNull()?.instanceId
    }

/**
 * Reads it from the profile the connection already keeps, which is `GET settings/public` — public,
 * so no credential is involved — and cached for the connection. Best-effort: a server that does not
 * answer simply offers no Application URL.
 */
internal class ProfileApplicationUrlReader
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ApplicationUrlReader {
        override suspend fun read(): String? =
            attempt { connection.profile() }
                .getOrNull()
                ?.settings
                ?.applicationUrlOrNull()
    }
