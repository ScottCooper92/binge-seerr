package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import androidx.room.withTransaction

/**
 * The request cache behind a seam, as [IssueStore] is, so the mediator and the browser are tested
 * against a fake rather than a database. An implementation owns the two-table writes: the rows and
 * the list's cursor.
 */
interface RequestStore {
    fun pagingSource(listKey: String): PagingSource<Int, RequestEntity>

    suspend fun nextSkip(listKey: String): Int?

    /** The cached row for [requestId] from any list, or null when no list has cached it. */
    suspend fun byId(requestId: Int): RequestEntity?

    /** Replaces one list's slice with its first page; [nextSkip] is null when that page was the last. */
    suspend fun refresh(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    )

    /** Adds one list's next page and moves its cursor on, to null at the last page. */
    suspend fun append(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    )

    /** Moves a request to [status] (a raw `SeerrRequestStatusCode`) in every list that cached it. */
    suspend fun updateStatus(
        requestId: Int,
        status: Int,
    )

    suspend fun delete(requestId: Int)

    /** Every list's rows and cursors: the saved server changed, so none of it is this server's. */
    suspend fun clearAll()
}

class RoomRequestStore(
    private val db: SeerrCacheDatabase,
) : RequestStore {
    private val requests get() = db.requestDao()
    private val keys get() = db.requestRemoteKeyDao()

    override fun pagingSource(listKey: String): PagingSource<Int, RequestEntity> = requests.pagingSource(listKey)

    override suspend fun nextSkip(listKey: String): Int? = keys.nextSkip(listKey)

    override suspend fun byId(requestId: Int): RequestEntity? = requests.byId(requestId)

    override suspend fun refresh(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    ) = db.withTransaction {
        this.requests.clear(listKey)
        this.requests.upsertAll(requests)
        keys.upsert(RequestRemoteKeyEntity(listKey, nextSkip))
    }

    override suspend fun append(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    ) = db.withTransaction {
        this.requests.upsertAll(requests)
        keys.upsert(RequestRemoteKeyEntity(listKey, nextSkip))
    }

    override suspend fun updateStatus(
        requestId: Int,
        status: Int,
    ) = requests.updateStatus(requestId, status)

    override suspend fun delete(requestId: Int) = requests.delete(requestId)

    override suspend fun clearAll() =
        db.withTransaction {
            requests.clearAll()
            keys.clearAll()
        }
}

/** No cache at all: writes go nowhere. What a build or a test that has not wired one gets. */
object NoRequestStore : RequestStore {
    override fun pagingSource(listKey: String): PagingSource<Int, RequestEntity> = error("NoRequestStore holds no rows")

    override suspend fun nextSkip(listKey: String): Int? = null

    override suspend fun byId(requestId: Int): RequestEntity? = null

    override suspend fun refresh(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    ) = Unit

    override suspend fun append(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    ) = Unit

    override suspend fun updateStatus(
        requestId: Int,
        status: Int,
    ) = Unit

    override suspend fun delete(requestId: Int) = Unit

    override suspend fun clearAll() = Unit
}
