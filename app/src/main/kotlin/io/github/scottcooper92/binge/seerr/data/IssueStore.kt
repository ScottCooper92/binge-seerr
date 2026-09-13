package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import androidx.room.withTransaction

/**
 * The issue cache behind a seam, so the mediator and the browser are tested against a fake rather
 * than a database. An implementation owns the two-table writes: the rows and the list's cursor.
 */
interface IssueStore {
    fun pagingSource(
        listKey: String,
        status: String?,
    ): PagingSource<Int, IssueEntity>

    suspend fun nextSkip(listKey: String): Int?

    /** Replaces one list's slice with its first page; [nextSkip] is null when that page was the last. */
    suspend fun refresh(
        listKey: String,
        issues: List<IssueEntity>,
        nextSkip: Int?,
    )

    /** Adds one list's next page and moves its cursor on, to null at the last page. */
    suspend fun append(
        listKey: String,
        issues: List<IssueEntity>,
        nextSkip: Int?,
    )

    suspend fun updateStatus(
        issueId: Int,
        status: String,
    )

    suspend fun delete(issueId: Int)

    /** Every list's rows and cursors: the saved server changed, so none of it is this server's. */
    suspend fun clearAll()
}

class RoomIssueStore(
    private val db: IssuesDatabase,
) : IssueStore {
    private val issues get() = db.issueDao()
    private val keys get() = db.remoteKeyDao()

    override fun pagingSource(
        listKey: String,
        status: String?,
    ): PagingSource<Int, IssueEntity> = issues.pagingSource(listKey, status)

    override suspend fun nextSkip(listKey: String): Int? = keys.nextSkip(listKey)

    override suspend fun refresh(
        listKey: String,
        issues: List<IssueEntity>,
        nextSkip: Int?,
    ) = db.withTransaction {
        this.issues.clear(listKey)
        this.issues.upsertAll(issues)
        keys.upsert(IssueRemoteKeyEntity(listKey, nextSkip))
    }

    override suspend fun append(
        listKey: String,
        issues: List<IssueEntity>,
        nextSkip: Int?,
    ) = db.withTransaction {
        this.issues.upsertAll(issues)
        keys.upsert(IssueRemoteKeyEntity(listKey, nextSkip))
    }

    override suspend fun updateStatus(
        issueId: Int,
        status: String,
    ) = issues.updateStatus(issueId, status)

    override suspend fun delete(issueId: Int) = issues.delete(issueId)

    override suspend fun clearAll() =
        db.withTransaction {
            issues.clearAll()
            keys.clearAll()
        }
}
