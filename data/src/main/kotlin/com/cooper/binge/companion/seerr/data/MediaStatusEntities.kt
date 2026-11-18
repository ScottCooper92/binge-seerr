package com.cooper.binge.companion.seerr.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * The last status the server gave for one title, and when it was asked.
 *
 * The message is stored whole rather than as a column per field: the contract only ever adds fields,
 * and this table is dropped on a schema change anyway, so columns would be migration work for a cache.
 *
 * @property status The contract's `RequestStatus` message, serialised and Base64-encoded, minus its
 *   allowed actions. Those are what this user may do *now*, so they are recomputed on every read.
 * @property requesterIds `request:user` id pairs: the server fact that recomputation needs and the message lacks.
 */
@Entity(tableName = "media_status", primaryKeys = ["mediaType", "tmdbId"])
data class MediaStatusEntity(
    val mediaType: Int,
    val tmdbId: Int,
    val status: String,
    val fetchedAtMillis: Long,
    val requesterIds: String,
)

@Dao
interface MediaStatusDao {
    @Query("SELECT * FROM media_status WHERE mediaType = :mediaType AND tmdbId = :tmdbId")
    suspend fun find(
        mediaType: Int,
        tmdbId: Int,
    ): MediaStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: MediaStatusEntity)

    @Query("DELETE FROM media_status")
    suspend fun clear()
}
