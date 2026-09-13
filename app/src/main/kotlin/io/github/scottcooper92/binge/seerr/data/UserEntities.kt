package io.github.scottcooper92.binge.seerr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A cached user row. [listKey] is the sort it was loaded in, so each order owns its own slice and
 * [orderIndex] is the server's position in it. [permissions] is the raw bitmask, kept whole so an
 * edit that changes some bits leaves the rest as the server had them.
 */
@Entity(tableName = "users", primaryKeys = ["listKey", "id"])
data class UserEntity(
    val listKey: String,
    val id: Int,
    val name: String,
    val email: String?,
    val handle: String?,
    val avatarUrl: String?,
    val origin: String,
    val permissions: Int,
    val requestCount: Int,
    val createdAtMillis: Long?,
    val orderIndex: Int,
)

/** One list's pagination cursor: the next server `skip`, or null once its last page is cached. */
@Entity(tableName = "user_remote_keys")
data class UserRemoteKeyEntity(
    @PrimaryKey val listKey: String,
    val nextSkip: Int?,
)
