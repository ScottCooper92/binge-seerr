package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.annotation.StringRes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.data.IssueEntity
import io.github.scottcooper92.binge.seerr.seerr.HydratedTitle
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestUserDto
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateTone
import java.time.Instant
import java.time.OffsetDateTime

private const val MEDIA_TYPE_MOVIE = "movie"
private const val MEDIA_TYPE_TV = "tv"

@StringRes
internal fun IssueFilter.labelRes(): Int =
    when (this) {
        IssueFilter.Open -> R.string.issues_filter_open
        IssueFilter.Resolved -> R.string.issues_filter_resolved
        IssueFilter.All -> R.string.issues_filter_all
    }

@StringRes
internal fun IssueFilter.emptyMessageRes(): Int =
    when (this) {
        IssueFilter.Open -> R.string.issues_empty_open
        IssueFilter.Resolved -> R.string.issues_empty_resolved
        IssueFilter.All -> R.string.issues_empty_all
    }

/** The stored status a filter selects on, or null for the unfiltered list. */
internal fun IssueFilter.statusValue(): String? =
    when (this) {
        IssueFilter.Open -> IssueStatus.Open.name
        IssueFilter.Resolved -> IssueStatus.Resolved.name
        IssueFilter.All -> null
    }

@StringRes
internal fun IssueSort.labelRes(): Int =
    when (this) {
        IssueSort.Added -> R.string.requests_sort_added
        IssueSort.Modified -> R.string.requests_sort_modified
    }

@StringRes
internal fun IssueStatus.labelRes(): Int =
    when (this) {
        IssueStatus.Open -> R.string.issue_state_open
        IssueStatus.Resolved -> R.string.issue_state_resolved
    }

internal fun IssueStatus.tone(): RequestStateTone =
    when (this) {
        IssueStatus.Open -> RequestStateTone.Pending
        IssueStatus.Resolved -> RequestStateTone.Success
    }

@StringRes
internal fun IssueType.labelRes(): Int =
    when (this) {
        IssueType.Video -> R.string.issue_type_video
        IssueType.Audio -> R.string.issue_type_audio
        IssueType.Subtitles -> R.string.issue_type_subtitles
        IssueType.Other -> R.string.issue_type_other
    }

/** An unmapped status reads as open, so an issue that may still need attention is never hidden. */
internal fun SeerrIssueStatusCode?.toIssueStatus(): IssueStatus =
    if (this == SeerrIssueStatusCode.Resolved) IssueStatus.Resolved else IssueStatus.Open

/** Null for an issue on media this app does not render; the row would have nothing to say. */
suspend fun SeerrIssueDto.toIssueEntity(
    api: SeerrApi,
    hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
    listKey: String,
    orderIndex: Int,
): IssueEntity? {
    val media = media ?: return null
    val mediaType =
        when (media.mediaType) {
            MEDIA_TYPE_MOVIE -> RequestMediaType.Movie
            MEDIA_TYPE_TV -> RequestMediaType.Tv
            else -> return null
        }
    val details = hydrate(api, media.mediaType, media.tmdbId)
    return IssueEntity(
        listKey = listKey,
        id = id,
        tmdbId = media.tmdbId,
        mediaType = mediaType.name,
        title = details?.title,
        posterUrl = details?.posterUrl,
        year = details?.year,
        issueType = (IssueType.entries.firstOrNull { it.code == issueType } ?: IssueType.Other).name,
        status = status.toIssueStatus().name,
        reportedBy = createdBy?.displayString(),
        reportedById = createdBy?.id,
        commentCount = comments.size,
        createdAtMillis = createdAt?.toEpochMillisOrNull(),
        updatedAtMillis = updatedAt?.toEpochMillisOrNull(),
        problem = comments.firstOrNull()?.message?.takeIf { it.isNotBlank() },
        problemSeason = problemSeason,
        problemEpisode = problemEpisode,
        orderIndex = orderIndex,
    )
}

fun IssueEntity.toIssueItem(): IssueItem =
    IssueItem(
        id = id,
        tmdbId = tmdbId,
        mediaType = RequestMediaType.valueOf(mediaType),
        title = title,
        posterUrl = posterUrl,
        year = year,
        type = IssueType.valueOf(issueType),
        status = IssueStatus.valueOf(status),
        reportedBy = reportedBy,
        reportedById = reportedById,
        commentCount = commentCount,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        problem = problem,
        problemSeason = problemSeason,
        problemEpisode = problemEpisode,
    )

/** Email is a last resort and masked to its local part. */
private fun SeerrRequestUserDto.displayString(): String? =
    listOfNotNull(displayName, username).firstOrNull { it.isNotBlank() } ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }

private fun String.toEpochMillisOrNull(): Long? =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
