package io.github.scottcooper92.binge.seerr.data

import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.v1.MediaId
import java.util.Base64

/** One cached status and the moment the server gave it. */
data class CachedStatus(
    val status: RequestStatus,
    val fetchedAtMillis: Long,
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
        status: RequestStatus,
        fetchedAtMillis: Long,
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
            decodeStatus(row.status)?.let { CachedStatus(it, row.fetchedAtMillis) }
        }

    override suspend fun put(
        media: MediaId,
        status: RequestStatus,
        fetchedAtMillis: Long,
    ) {
        dao.upsert(
            MediaStatusEntity(
                mediaType = media.mediaTypeValue,
                tmdbId = media.tmdbId,
                status = encodeStatus(status),
                fetchedAtMillis = fetchedAtMillis,
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

/** No cache at all: every lookup misses. What a build or a test that has not wired one gets. */
object NoMediaStatusStore : MediaStatusStore {
    override suspend fun find(media: MediaId): CachedStatus? = null

    override suspend fun put(
        media: MediaId,
        status: RequestStatus,
        fetchedAtMillis: Long,
    ) = Unit

    override suspend fun clearAll() = Unit
}
