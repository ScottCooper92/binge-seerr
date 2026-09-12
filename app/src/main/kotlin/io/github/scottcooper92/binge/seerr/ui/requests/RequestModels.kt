package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode

/** The browser's filters, each carrying the server's own `filter` value, in chip order. */
enum class RequestFilter(
    val apiValue: String,
) {
    All("all"),
    Pending("pending"),
    Approved("approved"),
    Processing("processing"),
    Available("available"),
    Unavailable("unavailable"),
    Failed("failed"),
}

enum class RequestSort(
    val apiValue: String,
) {
    Added("added"),
    Modified("modified"),
}

/** The server-wide totals behind the chips; the filters without a bucket show no count. */
data class RequestCounts(
    val total: Int,
    val pending: Int,
    val approved: Int,
    val processing: Int,
    val available: Int,
) {
    fun countFor(filter: RequestFilter): Int? =
        when (filter) {
            RequestFilter.All -> total
            RequestFilter.Pending -> pending
            RequestFilter.Approved -> approved
            RequestFilter.Processing -> processing
            RequestFilter.Available -> available
            RequestFilter.Unavailable, RequestFilter.Failed -> null
        }
}

enum class RequestMediaType { Movie, Tv }

/** The one aggregate bar over a request's active downloads. */
data class RequestDownload(
    val fraction: Float,
    val etaMinutes: Int?,
    val downloading: Boolean,
)

/**
 * One row of the browser. The title, poster and year come from a per-row lookup the list payload
 * does not carry, and are null when it failed: the row then shows the media type as its title.
 */
data class RequestItem(
    val id: Int,
    val tmdbId: Int,
    val mediaType: RequestMediaType,
    val title: String?,
    val posterUrl: String?,
    val year: String?,
    val requestedBy: String?,
    val requestedAtMillis: Long?,
    val status: SeerrRequestStatusCode?,
    val mediaStatus: SeerrMediaStatusCode?,
    val download: RequestDownload?,
    val seasonNumbers: List<Int>,
    val is4k: Boolean,
)

sealed interface RequestsUiState {
    data object Loading : RequestsUiState

    data class Ready(
        val filter: RequestFilter,
        val sort: RequestSort,
        val counts: RequestCounts?,
        val permissions: SeerrPermissions,
    ) : RequestsUiState
}
