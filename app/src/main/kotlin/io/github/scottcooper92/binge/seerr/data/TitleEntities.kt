package io.github.scottcooper92.binge.seerr.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * One title's display fields as `movie/{id}` or `tv/{id}` gave them, and when. Only a lookup that
 * succeeded is ever written, so a row here is never an "untitled" pin.
 */
@Entity(tableName = "title", primaryKeys = ["mediaType", "tmdbId"])
data class TitleEntity(
    val mediaType: String,
    val tmdbId: Int,
    val title: String?,
    val posterUrl: String?,
    val year: String?,
    val fetchedAtMillis: Long,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val certification: String? = null,
)

@Dao
interface TitleDao {
    /** The row, if it was fetched at or after [since]; an older one reads as absent. */
    @Query("SELECT * FROM title WHERE mediaType = :mediaType AND tmdbId = :tmdbId AND fetchedAtMillis >= :since")
    suspend fun find(
        mediaType: String,
        tmdbId: Int,
        since: Long,
    ): TitleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TitleEntity)

    @Query("DELETE FROM title WHERE fetchedAtMillis < :before")
    suspend fun deleteOlderThan(before: Long)

    /** Keeps the [keep] most recently fetched rows, so the table cannot grow without bound. */
    @Query(
        "DELETE FROM title WHERE (mediaType, tmdbId) NOT IN " +
            "(SELECT mediaType, tmdbId FROM title ORDER BY fetchedAtMillis DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM title")
    suspend fun clear()
}
