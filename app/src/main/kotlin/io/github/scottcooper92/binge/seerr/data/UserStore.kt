package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import androidx.room.withTransaction

/** The user cache behind a seam, as [IssueStore] is, so the mediator and the browser are tested against a fake. */
interface UserStore {
    fun pagingSource(listKey: String): PagingSource<Int, UserEntity>

    suspend fun nextSkip(listKey: String): Int?

    suspend fun refresh(
        listKey: String,
        users: List<UserEntity>,
        nextSkip: Int?,
    )

    suspend fun append(
        listKey: String,
        users: List<UserEntity>,
        nextSkip: Int?,
    )

    suspend fun updatePermissions(
        ids: List<Int>,
        permissions: Int,
    )

    /** The raw bitmask currently cached for each of [ids], so an edit can be seeded from and preserve it. */
    suspend fun permissionsFor(ids: List<Int>): List<Int>

    suspend fun delete(userId: Int)

    suspend fun clearAll()
}

class RoomUserStore(
    private val db: SeerrCacheDatabase,
) : UserStore {
    private val users get() = db.userDao()
    private val keys get() = db.userRemoteKeyDao()

    override fun pagingSource(listKey: String): PagingSource<Int, UserEntity> = users.pagingSource(listKey)

    override suspend fun nextSkip(listKey: String): Int? = keys.nextSkip(listKey)

    override suspend fun refresh(
        listKey: String,
        users: List<UserEntity>,
        nextSkip: Int?,
    ) = db.withTransaction {
        this.users.clear(listKey)
        this.users.upsertAll(users)
        keys.upsert(UserRemoteKeyEntity(listKey, nextSkip))
    }

    override suspend fun append(
        listKey: String,
        users: List<UserEntity>,
        nextSkip: Int?,
    ) = db.withTransaction {
        this.users.upsertAll(users)
        keys.upsert(UserRemoteKeyEntity(listKey, nextSkip))
    }

    override suspend fun updatePermissions(
        ids: List<Int>,
        permissions: Int,
    ) = users.updatePermissions(ids, permissions)

    override suspend fun permissionsFor(ids: List<Int>): List<Int> = users.permissionsFor(ids)

    override suspend fun delete(userId: Int) = users.delete(userId)

    override suspend fun clearAll() =
        db.withTransaction {
            users.clearAll()
            keys.clearAll()
        }
}
