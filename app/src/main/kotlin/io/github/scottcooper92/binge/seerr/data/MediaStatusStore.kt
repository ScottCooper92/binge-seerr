package io.github.scottcooper92.binge.seerr.data

import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.v1.MediaId
import java.util.Base64

/**
 * One cached status and the moment the server gave it. [requesterIds] maps each request's id to the
 * id of the user who made it: the contract carries only a display name, and the per-request actions
 * turn on whether the request is the signed-in user's own.
 */
data class CachedStatus(
    val status: RequestStatus,
    val fetchedAtMillis: Long,
    val requesterIds: Map<Int, Int> = emptyMap(),
)

/**
 * The title-status cache behind a seam, so the service is tested against a fake rather than a
 * database. It must not live in memory: many users open this app once to sign in, and after that
 * the process only runs because a host bound the service — a cache scoped to the process would be
 * empty on almost every lookup that matters.
 */
interface MediaStatusStore {
    suspend fun find(media: MediaId): CachedStatus?

    suspend fun put(
        media: MediaId,
        cached: CachedStatus,
    )

    /** Every row: the server changed, someone else signed in, or a write made all of it suspect. */
    suspend fun clearAll()
}

class RoomMediaStatusStore(
    db: SeerrCacheDatabase,
) : MediaStatusStore {
    private val dao = db.mediaStatusDao()

    override suspend fun find(media: MediaId): CachedStatus? =
        dao.find(media.mediaTypeValue, media.tmdbId)?.let { row ->
            decodeStatus(row.status)?.let { CachedStatus(it, row.fetchedAtMillis, decodeRequesterIds(row.requesterIds)) }
        }

    override suspend fun put(
        media: MediaId,
        cached: CachedStatus,
    ) {
        dao.upsert(
            MediaStatusEntity(
                mediaType = media.mediaTypeValue,
                tmdbId = media.tmdbId,
                status = encodeStatus(cached.status),
                fetchedAtMillis = cached.fetchedAtMillis,
                requesterIds = encodeRequesterIds(cached.requesterIds),
            ),
        )
    }

    override suspend fun clearAll() = dao.clear()
}

internal fun encodeStatus(status: RequestStatus): String = Base64.getEncoder().encodeToString(status.toByteArray())

/**
 * A row this build cannot read is a miss, not an error: the message is only ever extended, so this
 * is a downgrade or a corrupted row, and either should send the caller to the server rather than
 * throw into the host's call.
 */
internal fun decodeStatus(encoded: String): RequestStatus? =
    runCatching { RequestStatus.parseFrom(Base64.getDecoder().decode(encoded)) }.getOrNull()

internal fun encodeRequesterIds(ids: Map<Int, Int>): String = ids.entries.joinToString(",") { (request, user) -> "$request:$user" }

/** A pair that does not parse is dropped, which only ever withholds an action: nobody reads as the requester. */
internal fun decodeRequesterIds(encoded: String): Map<Int, Int> =
    encoded
        .split(',')
        .mapNotNull { pair ->
            val (request, user) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            request.toIntOrNull()?.let { r -> user.toIntOrNull()?.let { u -> r to u } }
        }.toMap()

/** No cache at all: every lookup misses. What a build or a test that has not wired one gets. */
object NoMediaStatusStore : MediaStatusStore {
    override suspend fun find(media: MediaId): CachedStatus? = null

    override suspend fun put(
        media: MediaId,
        cached: CachedStatus,
    ) = Unit

    override suspend fun clearAll() = Unit
}
