package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * The request cache as a list in memory, paged like the database: every write invalidates the
 * sources handed out, so a pager over it reloads exactly as it would over Room.
 */
class FakeRequestStore : RequestStore {
    var rows: List<RequestEntity> = emptyList()
        private set
    private val cursors = mutableMapOf<String, Int?>()
    private val sources = mutableListOf<PagingSource<Int, RequestEntity>>()
    val refreshed = mutableListOf<Pair<List<RequestEntity>, Int?>>()
    val appended = mutableListOf<Pair<List<RequestEntity>, Int?>>()

    override fun pagingSource(listKey: String): PagingSource<Int, RequestEntity> = ListSource(listKey).also { sources += it }

    override suspend fun nextSkip(listKey: String): Int? = cursors[listKey]

    override suspend fun byId(requestId: Int): RequestEntity? = rows.firstOrNull { it.id == requestId }

    override suspend fun refresh(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    ) {
        refreshed += requests to nextSkip
        cursors[listKey] = nextSkip
        write(rows.filterNot { it.listKey == listKey } + requests)
    }

    override suspend fun append(
        listKey: String,
        requests: List<RequestEntity>,
        nextSkip: Int?,
    ) {
        appended += requests to nextSkip
        cursors[listKey] = nextSkip
        write(rows + requests)
    }

    override suspend fun updateStatus(
        requestId: Int,
        status: Int,
    ) = write(rows.map { if (it.id == requestId) it.copy(status = status) else it })

    override suspend fun delete(requestId: Int) = write(rows.filterNot { it.id == requestId })

    override suspend fun clearAll() {
        cursors.clear()
        write(emptyList())
    }

    private fun write(next: List<RequestEntity>) {
        rows = next
        sources.toList().forEach { it.invalidate() }
        sources.clear()
    }

    private inner class ListSource(
        private val listKey: String,
    ) : PagingSource<Int, RequestEntity>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RequestEntity> {
            val all = rows.filter { it.listKey == listKey }.sortedBy { it.orderIndex }
            val start = params.key ?: 0
            val end = minOf(start + params.loadSize, all.size)
            return LoadResult.Page(
                data = all.subList(start, end),
                prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
                nextKey = if (end >= all.size) null else end,
            )
        }

        override fun getRefreshKey(state: PagingState<Int, RequestEntity>): Int? = null
    }
}
