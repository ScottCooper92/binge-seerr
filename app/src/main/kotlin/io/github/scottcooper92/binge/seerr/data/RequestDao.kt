package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RequestDao {
    /** One list's slice in server order. The server decides which requests a filter holds. */
    @Query("SELECT * FROM requests WHERE listKey = :listKey ORDER BY orderIndex ASC")
    fun pagingSource(listKey: String): PagingSource<Int, RequestEntity>

    /** Any slice's copy of the request: a detail screen seeds its header from whichever list the user tapped it in. */
    @Query("SELECT * FROM requests WHERE id = :id LIMIT 1")
    suspend fun byId(id: Int): RequestEntity?

    @Upsert
    suspend fun upsertAll(requests: List<RequestEntity>)

    /** No list key: a status change reaches every slice that cached the request. */
    @Query("UPDATE requests SET status = :status WHERE id = :id")
    suspend fun updateStatus(
        id: Int,
        status: Int,
    )

    @Query("DELETE FROM requests WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM requests WHERE listKey = :listKey")
    suspend fun clear(listKey: String)

    @Query("DELETE FROM requests")
    suspend fun clearAll()
}

@Dao
interface RequestRemoteKeyDao {
    @Query("SELECT nextSkip FROM request_remote_keys WHERE listKey = :listKey")
    suspend fun nextSkip(listKey: String): Int?

    @Upsert
    suspend fun upsert(key: RequestRemoteKeyEntity)

    @Query("DELETE FROM request_remote_keys")
    suspend fun clearAll()
}
