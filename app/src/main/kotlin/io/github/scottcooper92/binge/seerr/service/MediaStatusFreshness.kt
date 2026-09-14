package io.github.scottcooper92.binge.seerr.service

import com.binge.integration.contracts.request.v1.Availability
import com.binge.integration.contracts.request.v1.DownloadState
import com.binge.integration.contracts.request.v1.RequestStatus
import io.github.scottcooper92.binge.seerr.data.CachedStatus

/**
 * A few minutes: a pending request is waiting on a person, and a partly-available show on the next
 * episode landing. Neither moves on a timescale a host would notice, but both move.
 */
private const val WAITING_MAX_AGE_MILLIS = 3 * 60 * 1000L

/**
 * Half an hour: an available title stays available, an unrequested one stays unrequested, and a
 * blocklisted one stays blocked — until somebody acts, and an action clears the whole cache.
 */
private const val SETTLED_MAX_AGE_MILLIS = 30 * 60 * 1000L

/**
 * How long a cached status stands before the server has to be asked again, by where the title is.
 *
 * Something in flight goes stale at the poll interval, so a host reading a downloading title never
 * sees a row older than one poll would have been. Everything else is graded by how fast it can
 * actually change. The numbers are starting points to tune, which is why they live together.
 */
class MediaStatusFreshness(
    private val movingMaxAgeMillis: Long,
) {
    /** Null where the status says nothing worth keeping, which is never written and never read. */
    fun maxAgeMillis(status: RequestStatus): Long? =
        when {
            status.availability == Availability.AVAILABILITY_UNSPECIFIED -> null
            status.isMoving() -> movingMaxAgeMillis
            status.availability == Availability.AVAILABILITY_PENDING -> WAITING_MAX_AGE_MILLIS
            status.availability == Availability.AVAILABILITY_PARTIALLY_AVAILABLE -> WAITING_MAX_AGE_MILLIS
            else -> SETTLED_MAX_AGE_MILLIS
        }

    fun isFresh(
        cached: CachedStatus,
        nowMillis: Long,
    ): Boolean {
        val maxAge = maxAgeMillis(cached.status) ?: return false
        val age = nowMillis - cached.fetchedAtMillis
        // A row from the future is a clock that moved, not a fresh row: ask the server again.
        return age in 0 until maxAge
    }
}

/**
 * Processing, or anything on its way down. A queued download counts: what it is waiting for is the
 * client picking it up, which can happen at any moment.
 */
private fun RequestStatus.isMoving(): Boolean =
    availability == Availability.AVAILABILITY_PROCESSING ||
        (hasDownload() && download.state != DownloadState.DOWNLOAD_STATE_UNSPECIFIED)
