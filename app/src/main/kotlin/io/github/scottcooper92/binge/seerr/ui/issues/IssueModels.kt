package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

/** The browser's filters, each carrying the server's own `filter` value, in chip order. */
enum class IssueFilter(
    val apiValue: String,
) {
    Open("open"),
    Resolved("resolved"),
    All("all"),
}

enum class IssueSort(
    val apiValue: String,
) {
    Added("added"),
    Modified("modified"),
}

enum class IssueStatus { Open, Resolved }

/** The server-wide totals behind the chips. */
data class IssueCounts(
    val total: Int,
    val open: Int,
    val resolved: Int,
) {
    fun countFor(filter: IssueFilter): Int =
        when (filter) {
            IssueFilter.All -> total
            IssueFilter.Open -> open
            IssueFilter.Resolved -> resolved
        }
}

/**
 * One row of the browser. The title, poster and year come from a per-row lookup the list payload
 * does not carry; [problem] is the report's opening line, and the season and episode scope a
 * show's issue where the reporter narrowed it.
 */
data class IssueItem(
    val id: Int,
    val tmdbId: Int,
    val mediaType: RequestMediaType,
    val title: String?,
    val posterUrl: String?,
    val year: String?,
    val type: IssueType,
    val status: IssueStatus,
    val reportedBy: String?,
    val reportedById: Int?,
    val commentCount: Int,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val problem: String?,
    val problemSeason: Int?,
    val problemEpisode: Int?,
)

/** Who is looking: the permissions decide whether the list is everyone's issues or only the user's own. */
data class IssueListScope(
    val permissions: SeerrPermissions = SeerrPermissions(),
    val currentUserId: Int? = null,
) {
    /** The `requestedBy` the list is narrowed to, or null for a user who may see everyone's. */
    val requestedBy: Int? get() = currentUserId?.takeUnless { permissions.canManageIssues || permissions.canViewIssues }
}

sealed interface IssuesUiState {
    data object Loading : IssuesUiState

    data class Ready(
        val filter: IssueFilter,
        val sort: IssueSort,
        val counts: IssueCounts?,
        val scope: IssueListScope,
    ) : IssuesUiState
}
