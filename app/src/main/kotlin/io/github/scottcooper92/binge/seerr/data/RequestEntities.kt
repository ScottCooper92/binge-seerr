package io.github.scottcooper92.binge.seerr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A cached request row. [listKey] is the list it was loaded for (a filter in a sort, for one
 * scope), so every list owns its own slice and a refresh of one never touches another;
 * [orderIndex] is the server's position across pages. Enums are stored by name and the status
 * codes by their raw value. A request with no active download has a null [downloadFraction];
 * [seasonNumbers] is comma-separated.
 */
@Entity(tableName = "requests", primaryKeys = ["listKey", "id"])
data class RequestEntity(
    val listKey: String,
    val id: Int,
    val tmdbId: Int,
    val mediaType: String,
    val title: String?,
    val posterUrl: String?,
    val year: String?,
    val requestedBy: String?,
    val requestedById: Int?,
    val requestedAtMillis: Long?,
    val status: Int?,
    val mediaStatus: Int?,
    val downloadFraction: Float?,
    val downloadEtaMinutes: Int?,
    val downloading: Boolean,
    val seasonNumbers: String,
    val is4k: Boolean,
    val orderIndex: Int,
)

/** One list's pagination cursor: the next server `skip`, or null once its last page is cached. */
@Entity(tableName = "request_remote_keys")
data class RequestRemoteKeyEntity(
    @PrimaryKey val listKey: String,
    val nextSkip: Int?,
)
