package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.attempt
import javax.inject.Inject

/** The connected server's Application URL, normalised, or null where the administrator left it blank. */
fun interface ApplicationUrlReader {
    suspend fun read(): String?
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
