package io.github.scottcooper92.binge.seerr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A cached issue row. [listKey] is the list it was loaded for (a filter in a sort), so every list
 * owns its own slice and a refresh of one never touches another; [orderIndex] is the server's
 * position across pages. Enums are stored by name.
 */
@Entity(tableName = "issues", primaryKeys = ["listKey", "id"])
data class IssueEntity(
    val listKey: String,
    val id: Int,
    val tmdbId: Int,
    val mediaType: String,
    val title: String?,
    val posterUrl: String?,
    val year: String?,
    val issueType: String,
    val status: String,
    val reportedBy: String?,
    val reportedById: Int?,
    val commentCount: Int,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val problem: String?,
    val problemSeason: Int?,
    val problemEpisode: Int?,
    val orderIndex: Int,
)

/** One list's pagination cursor: the next server `skip`, or null once its last page is cached. */
@Entity(tableName = "issue_remote_keys")
data class IssueRemoteKeyEntity(
    @PrimaryKey val listKey: String,
    val nextSkip: Int?,
)
