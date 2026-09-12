package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

/** The user cache as a list in memory, paged like the database: every write invalidates the sources handed out. */
class FakeUserStore : UserStore {
    var rows: List<UserEntity> = emptyList()
        private set
    private val cursors = mutableMapOf<String, Int?>()
    private val sources = mutableListOf<PagingSource<Int, UserEntity>>()
    val refreshed = mutableListOf<Pair<List<UserEntity>, Int?>>()
    val appended = mutableListOf<Pair<List<UserEntity>, Int?>>()

    override fun pagingSource(listKey: String): PagingSource<Int, UserEntity> = ListSource(listKey).also { sources += it }

    override suspend fun nextSkip(listKey: String): Int? = cursors[listKey]

    override suspend fun refresh(
        listKey: String,
        users: List<UserEntity>,
        nextSkip: Int?,
    ) {
        refreshed += users to nextSkip
        cursors[listKey] = nextSkip
        write(rows.filterNot { it.listKey == listKey } + users)
    }

    override suspend fun append(
        listKey: String,
        users: List<UserEntity>,
        nextSkip: Int?,
    ) {
        appended += users to nextSkip
        cursors[listKey] = nextSkip
        write(rows + users)
    }

    override suspend fun updatePermissions(
        ids: List<Int>,
        permissions: Int,
    ) = write(rows.map { if (it.id in ids) it.copy(permissions = permissions) else it })

    override suspend fun permissionsFor(ids: List<Int>): List<Int> = rows.filter { it.id in ids }.map { it.permissions }

    override suspend fun delete(userId: Int) = write(rows.filterNot { it.id == userId })

    override suspend fun clearAll() {
        cursors.clear()
        write(emptyList())
    }

    private fun write(next: List<UserEntity>) {
        rows = next
        sources.toList().forEach { it.invalidate() }
        sources.clear()
    }

    private inner class ListSource(
        private val listKey: String,
    ) : PagingSource<Int, UserEntity>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UserEntity> {
            val all = rows.filter { it.listKey == listKey }.sortedBy { it.orderIndex }
            val start = params.key ?: 0
            val end = minOf(start + params.loadSize, all.size)
            return LoadResult.Page(
                data = all.subList(start, end),
                prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
                nextKey = if (end >= all.size) null else end,
            )
        }

        override fun getRefreshKey(state: PagingState<Int, UserEntity>): Int? = null
    }
}
