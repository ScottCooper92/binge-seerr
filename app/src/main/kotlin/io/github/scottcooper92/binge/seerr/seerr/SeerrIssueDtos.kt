package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeerrIssuePageDto(
    @SerialName("pageInfo") val pageInfo: SeerrPageInfoDto = SeerrPageInfoDto(),
    @SerialName("results") val results: List<SeerrIssueDto> = emptyList(),
)

/**
 * One reported issue: its type and state, who filed it, the [media] it targets, and its thread.
 * [problemSeason] and [problemEpisode] scope a show's issue; `0` is the whole season, or all of it.
 */
@Serializable
data class SeerrIssueDto(
    @SerialName("id") val id: Int,
    @SerialName("issueType") val issueType: SeerrIssueTypeCode? = null,
    @SerialName("status") val status: SeerrIssueStatusCode? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    @SerialName("createdBy") val createdBy: SeerrRequestUserDto? = null,
    @SerialName("media") val media: SeerrRequestMediaDto? = null,
    @SerialName("comments") val comments: List<SeerrIssueCommentDto> = emptyList(),
    @SerialName("problemSeason") val problemSeason: Int? = null,
    @SerialName("problemEpisode") val problemEpisode: Int? = null,
)

/** Seerr's `IssueStatus` enum: 1 open, 2 resolved. */
@JvmInline
@Serializable
value class SeerrIssueStatusCode(
    val raw: Int,
) {
    companion object {
        val Open = SeerrIssueStatusCode(1)
        val Resolved = SeerrIssueStatusCode(2)
    }
}

@Serializable
data class SeerrIssueCommentDto(
    @SerialName("id") val id: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    @SerialName("user") val user: SeerrRequestUserDto? = null,
)
