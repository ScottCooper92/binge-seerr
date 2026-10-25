package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.v1.MediaId
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.CachedStatus
import com.cooper.binge.companion.seerr.data.MediaStatusStore
import com.cooper.binge.companion.seerr.seerr.destination
import com.cooper.binge.companion.seerr.seerr.details
import com.cooper.binge.companion.seerr.seerr.isSeerrTv
import com.cooper.binge.companion.seerr.seerr.requesterIds
import com.cooper.binge.companion.seerr.seerr.seerrMediaType
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toRequestStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A title's status as every REQUEST v1 read answers it, through the one status cache. [SeerrRequestService] composes it. */
internal class SeerrStatusReads(
    private val connection: SeerrConnection,
    private val statusCache: MediaStatusStore,
    private val freshness: MediaStatusFreshness,
    private val qualityLabels: QualityProfileLabels,
    private val clock: () -> Long,
) {
    /**
     * Each distinct title's status, looked up side by side. At most [MAX_PARALLEL_LOOKUPS] go to the
     * server at once, so a cold page does not open fifty connections to it.
     */
    suspend fun statusesOf(media: List<MediaId>): Map<MediaId, RequestStatus> {
        val lookups = Semaphore(MAX_PARALLEL_LOOKUPS)
        return coroutineScope {
            media
                .distinct()
                .map { title -> async { title to lookups.withPermit { status(title) } } }
                .awaitAll()
                .toMap()
        }
    }

    /**
     * The title's status, from the cache while it is young enough for where the title is, and from
     * the server otherwise.
     *
     * Allowed actions are never cached and never stored: the contract defines them as what this
     * user may do right now, so they are recomputed against the live permissions on every read of
     * the row. What is cached is the server's answer alone.
     */
    suspend fun status(
        media: MediaId,
        allowCached: Boolean = true,
    ): RequestStatus {
        val user = connection.authenticatedUser()
        val server = (if (allowCached) cachedStatus(media) else null) ?: fetchStatus(media)
        return user.toPermissions().withAllowedActions(
            server,
            viewerId = user.id,
            profile = connection.profile(),
            isTv = media.seerrMediaType().isSeerrTv(),
        )
    }

    suspend fun cachedStatus(media: MediaId): CachedStatus? = statusCache.find(media)?.takeIf { freshness.isFresh(it, clock()) }

    /**
     * The row carries the download ETA the server's answer was turned into, so a cached one is up
     * to its own maximum age out of date. That age is the poll interval for anything downloading,
     * which is the same staleness a host subscribed to [SeerrRequestService.observeStatus] already lives with.
     */
    private suspend fun fetchStatus(media: MediaId): CachedStatus {
        val now = clock()
        val info = connection.api().details(media).mediaInfo
        val isTv = media.seerrMediaType().isSeerrTv()
        val labels = qualityLabels.forDestinations(info?.requests.orEmpty().map { it.destination(isTv) })
        val fetched = CachedStatus(info.toRequestStatus(now, isTv, labels), now, info.requesterIds())
        if (freshness.maxAgeMillis(fetched.status) != null) statusCache.put(media, fetched)
        return fetched
    }

    private companion object {
        /** How many title lookups [statusesOf] sends to the server at once. */
        const val MAX_PARALLEL_LOOKUPS = 4
    }
}
