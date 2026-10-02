package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.inspectProfile

/**
 * Reads the server's profile, and answers a caller that queued behind a read that then failed with
 * that failure instead of probing again. An unreachable server's profile is never cached, so
 * without this every caller waiting on the connection's lock pays its own full timeout one after
 * another. A caller that arrives after the failure still probes: a retry is a retry.
 *
 * [inspect] is only called with the connection's lock held, so the failure needs no lock of its own;
 * [failures] is read before queueing, hence `@Volatile`.
 */
internal class ProfileReads {
    @Volatile
    var failures = 0
        private set

    private var latest: Pair<SeerrCredentials, Throwable>? = null

    /** [seen] is [failures] as it stood before the caller started waiting for the lock. */
    suspend fun inspect(
        saved: SeerrCredentials,
        api: SeerrApi,
        seen: Int,
    ): Result<SeerrServerProfile> {
        latest?.takeIf { failures > seen && it.first == saved }?.let { return Result.failure(it.second) }
        val read = api.inspectProfile(saved.variant)
        latest = read.exceptionOrNull()?.let { saved to it }
        if (latest != null) failures++
        return read
    }
}
