package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE listKey = :listKey ORDER BY orderIndex ASC")
    fun pagingSource(listKey: String): PagingSource<Int, UserEntity>

    @Upsert
    suspend fun upsertAll(users: List<UserEntity>)

    /** No list key: a permission change reaches every order that cached the user. */
    @Query("UPDATE users SET permissions = :permissions WHERE id IN (:ids)")
    suspend fun updatePermissions(
        ids: List<Int>,
        permissions: Int,
    )

    /** The raw bitmask cached for each of [ids], one per cached order — duplicates for a user are harmless to an OR-fold. */
    @Query("SELECT permissions FROM users WHERE id IN (:ids)")
    suspend fun permissionsFor(ids: List<Int>): List<Int>

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM users WHERE listKey = :listKey")
    suspend fun clear(listKey: String)

    @Query("DELETE FROM users")
    suspend fun clearAll()
}

@Dao
interface UserRemoteKeyDao {
    @Query("SELECT nextSkip FROM user_remote_keys WHERE listKey = :listKey")
    suspend fun nextSkip(listKey: String): Int?

    @Upsert
    suspend fun upsert(key: UserRemoteKeyEntity)

    @Query("DELETE FROM user_remote_keys")
    suspend fun clearAll()
}
