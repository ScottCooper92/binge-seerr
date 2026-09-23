package io.github.scottcooper92.binge.seerr.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * The last status the server gave for one title, and when it was asked.
 *
 * [status] is the contract's `RequestStatus` message, serialised and Base64-encoded, minus its
 * allowed actions — those are what this user may do *now* and are recomputed on every read, against
 * [requesterIds]: `request:user` id pairs, the one server fact they need that the message lacks. Storing
 * the message rather than a column per field is deliberate: the contract only ever adds fields, and
 * this table is dropped on a schema change anyway, so a column per field would be migration work
 * for a cache.
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
