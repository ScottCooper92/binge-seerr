package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface IssueDao {
    /**
     * One list's slice in server order. A null [status] is the unfiltered list; otherwise the
     * predicate is what drops a resolved issue from the open list the moment [updateStatus] runs.
     */
    @Query("SELECT * FROM issues WHERE listKey = :listKey AND (:status IS NULL OR status = :status) ORDER BY orderIndex ASC")
    fun pagingSource(
        listKey: String,
        status: String?,
    ): PagingSource<Int, IssueEntity>

    @Upsert
    suspend fun upsertAll(issues: List<IssueEntity>)

    /** No list key: a status change reaches every slice that cached the issue. */
    @Query("UPDATE issues SET status = :status WHERE id = :id")
    suspend fun updateStatus(
        id: Int,
        status: String,
    )

    @Query("DELETE FROM issues WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM issues WHERE listKey = :listKey")
    suspend fun clear(listKey: String)

    @Query("DELETE FROM issues")
    suspend fun clearAll()
}

@Dao
interface IssueRemoteKeyDao {
    @Query("SELECT nextSkip FROM issue_remote_keys WHERE listKey = :listKey")
    suspend fun nextSkip(listKey: String): Int?

    @Upsert
    suspend fun upsert(key: IssueRemoteKeyEntity)

    @Query("DELETE FROM issue_remote_keys")
    suspend fun clearAll()
}
