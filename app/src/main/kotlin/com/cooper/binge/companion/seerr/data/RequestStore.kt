package com.cooper.binge.companion.seerr.data

import androidx.paging.PagingSource
import com.binge.companion.sdk.paging.OpenPagingSources
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The request cache behind a seam, as [IssueStore] is, so the mediator and the browser are tested
 * against a fake rather than a database. An implementation owns the two-table writes: the rows and
 * the list's cursor.
 */
interface RequestStore {
    /**
     * The chip counts, which belong to the server rather than to a list, so whoever last read them writes them here and
     * every screen showing them updates (#1665). Null until a read has landed, and again after [clearAll].
     */
    val counts: StateFlow<RequestCounts?>

    fun setCounts(counts: RequestCounts)

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

    /**
     * Moves a request to [status] (a raw `SeerrRequestStatusCode`) in every list that cached it, and takes it out of
     * the slices of a filter on that status (see [statusFilters]) that it no longer matches. A slice it newly matches
     * is left to its next refresh, because where the row sorts there is the server's to say.
     */
    suspend fun updateStatus(
        requestId: Int,
        status: Int,
    )

    /** [updateStatus] with the server's own answer: a null part is one the answer did not carry, and is left as cached. */
    suspend fun updateServerState(
        requestId: Int,
        status: Int?,
        mediaStatus: Int?,
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
    private val sources = OpenPagingSources<Int, RequestEntity>()
    private val cachedCounts = MutableStateFlow<RequestCounts?>(null)
    override val counts: StateFlow<RequestCounts?> = cachedCounts.asStateFlow()
    private val writes =
        PagedWrites(
            db = db,
            sources = sources,
            clearList = { requests.clear(it) },
            upsertRows = { requests.upsertAll(it) },
            setCursor = { listKey, nextSkip -> keys.upsert(RequestRemoteKeyEntity(listKey, nextSkip)) },
            clearEverything = { requests.clearAll() },
            clearCursors = { keys.clearAll() },
        )

    override fun setCounts(counts: RequestCounts) {
        cachedCounts.value = counts
    }

    override fun pagingSource(listKey: String): PagingSource<Int, RequestEntity> = sources.track(requests.pagingSource(listKey))

    override suspend fun nextSkip(listKey: String): Int? = keys.nextSkip(listKey)

    override suspend fun byId(requestId: Int): RequestEntity? = requests.byId(requestId)

    override suspend fun refresh(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    ) = writes.refresh(listKey, requests, nextSkip)

    override suspend fun append(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    ) = writes.append(listKey, requests, nextSkip)

    override suspend fun updateStatus(
        requestId: Int,
        status: Int,
    ) = updateServerState(requestId, status, mediaStatus = null)

    override suspend fun updateServerState(
        requestId: Int,
        status: Int?,
        mediaStatus: Int?,
    ) = sources.afterWrite {
        requests.updateState(requestId, status, mediaStatus)
        if (status == null) return@afterWrite
        statusFilters.forEach { (filter, wanted) -> if (status != wanted) requests.dropFromFilter(requestId, "$filter:%") }
    }

    override suspend fun delete(requestId: Int) = sources.afterWrite { requests.delete(requestId) }

    override suspend fun clearAll() {
        cachedCounts.value = null
        writes.clearAll()
    }
}

/**
 * The list filters the server decides by the request's own status alone, as the `filter` of its `GET request`: a row
 * with another status is not in them. The other filters read the media's status too, which a moderation's answer may
 * not have settled yet, so they are left to refresh.
 */
internal val statusFilters: Map<String, Int> =
    mapOf(
        RequestFilter.Pending.apiValue to SeerrRequestStatusCode.Pending.raw,
        RequestFilter.Approved.apiValue to SeerrRequestStatusCode.Approved.raw,
        RequestFilter.Failed.apiValue to SeerrRequestStatusCode.Failed.raw,
    )

/** No cache at all: writes go nowhere. What a build or a test that has not wired one gets. */
object NoRequestStore : RequestStore {
    override val counts: StateFlow<RequestCounts?> = MutableStateFlow(null)

    override fun setCounts(counts: RequestCounts) = Unit

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

    override suspend fun updateServerState(
        requestId: Int,
        status: Int?,
        mediaStatus: Int?,
    ) = Unit

    override suspend fun delete(requestId: Int) = Unit

    override suspend fun clearAll() = Unit
}
