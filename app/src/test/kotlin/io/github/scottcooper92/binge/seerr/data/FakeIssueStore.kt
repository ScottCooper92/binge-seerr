package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * The issue cache as a list in memory, paged like the database: every write invalidates the
 * sources handed out, so a pager over it reloads exactly as it would over Room.
 */
class FakeIssueStore : IssueStore {
    var rows: List<IssueEntity> = emptyList()
        private set
    private val cursors = mutableMapOf<String, Int?>()
    private val sources = mutableListOf<PagingSource<Int, IssueEntity>>()
    val refreshed = mutableListOf<Pair<List<IssueEntity>, Int?>>()
    val appended = mutableListOf<Pair<List<IssueEntity>, Int?>>()

    override fun pagingSource(
        listKey: String,
        status: String?,
    ): PagingSource<Int, IssueEntity> = ListSource(listKey, status).also { sources += it }

    override suspend fun nextSkip(listKey: String): Int? = cursors[listKey]

    override suspend fun refresh(
        listKey: String,
        issues: List<IssueEntity>,
        nextSkip: Int?,
    ) {
        refreshed += issues to nextSkip
        cursors[listKey] = nextSkip
        write(rows.filterNot { it.listKey == listKey } + issues)
    }

    override suspend fun append(
        listKey: String,
        issues: List<IssueEntity>,
        nextSkip: Int?,
    ) {
        appended += issues to nextSkip
        cursors[listKey] = nextSkip
        write(rows + issues)
    }

    override suspend fun updateStatus(
        issueId: Int,
        status: String,
    ) = write(rows.map { if (it.id == issueId) it.copy(status = status) else it })

    override suspend fun delete(issueId: Int) = write(rows.filterNot { it.id == issueId })

    override suspend fun clearAll() {
        cursors.clear()
        write(emptyList())
    }

    private fun write(next: List<IssueEntity>) {
        rows = next
        sources.toList().forEach { it.invalidate() }
        sources.clear()
    }

    private inner class ListSource(
        private val listKey: String,
        private val status: String?,
    ) : PagingSource<Int, IssueEntity>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, IssueEntity> {
            val all = rows.filter { it.listKey == listKey && (status == null || it.status == status) }.sortedBy { it.orderIndex }
            val start = params.key ?: 0
            val end = minOf(start + params.loadSize, all.size)
            return LoadResult.Page(
                data = all.subList(start, end),
                prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
                nextKey = if (end >= all.size) null else end,
            )
        }

        override fun getRefreshKey(state: PagingState<Int, IssueEntity>): Int? = null
    }
}
