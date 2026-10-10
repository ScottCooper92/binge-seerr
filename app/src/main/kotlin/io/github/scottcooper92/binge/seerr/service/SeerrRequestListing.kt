package io.github.scottcooper92.binge.seerr.service

import com.binge.companion.contracts.request.v1.ListRequestsRequest
import com.binge.companion.contracts.request.v1.RequestFilter
import io.github.scottcooper92.binge.seerr.seerr.attempt
import java.util.Base64

/** What `ListRequests` answers with when the host leaves `page_size` at 0. */
internal const val DEFAULT_LIST_PAGE_SIZE = 20

/**
 * The most `ListRequests` answers in one page, whatever is asked. Each entry carries the title's
 * whole status, so this keeps a page well under the Binder transaction limit.
 */
internal const val MAX_LIST_PAGE_SIZE = 50

/** The most titles one `GetStatuses` call may name. The number is the contract's, not this app's. */
internal const val MAX_STATUSES_PER_CALL = 50

/**
 * One `ListRequests` filter as Seerr's `GET /request` query. Seerr narrows the list to the user's own
 * requests unless they hold `MANAGE_REQUESTS` or `REQUEST_VIEW`, so `ALL` is "every request this user
 * may see" without any check here. `MINE` asks for the user's own by id. The sort is newest first:
 * by request id, or by when the request last changed for `RECENTLY_AVAILABLE`, which is the closest
 * Seerr has to "when the title became available". How recent is left open: the host pages for as
 * long as it wants.
 */
internal enum class SeerrRequestQuery(
    val filter: String,
    val sort: String,
    val mine: Boolean = false,
) {
    Mine(filter = "all", sort = "added", mine = true),
    All(filter = "all", sort = "added"),
    AwaitingModeration(filter = "pending", sort = "added"),
    RecentlyAvailable(filter = "available", sort = "modified"),
}

/** The query for [this] filter; an unspecified or unknown one is INVALID_ARGUMENT, as the contract says. */
internal fun RequestFilter.toSeerrQuery(): SeerrRequestQuery =
    when (this) {
        RequestFilter.REQUEST_FILTER_MINE -> SeerrRequestQuery.Mine
        RequestFilter.REQUEST_FILTER_ALL -> SeerrRequestQuery.All
        RequestFilter.REQUEST_FILTER_AWAITING_MODERATION -> SeerrRequestQuery.AwaitingModeration
        RequestFilter.REQUEST_FILTER_RECENTLY_AVAILABLE -> SeerrRequestQuery.RecentlyAvailable
        RequestFilter.REQUEST_FILTER_UNSPECIFIED, RequestFilter.UNRECOGNIZED -> throw invalidArgument("filter must be set")
    }

/** The page size to ask Seerr for: 0 is this app's choice, and anything above the cap is cut to it. */
internal fun ListRequestsRequest.effectivePageSize(): Int {
    if (pageSize < 0) throw invalidArgument("page_size must not be negative, was $pageSize")
    return if (pageSize == 0) DEFAULT_LIST_PAGE_SIZE else pageSize.coerceAtMost(MAX_LIST_PAGE_SIZE)
}

/**
 * The opaque `page_token`: Seerr's `skip`, with the filter and the page size it was issued under.
 * The host treats it as opaque, so it is base64. It carries the filter and the size because the
 * contract makes a token valid only with the ones it was issued under, and this is how that is
 * checked. A token this app could not have written is INVALID_ARGUMENT.
 */
internal object ListRequestsPageToken {
    private const val VERSION = "v1"
    private const val FIELD_COUNT = 4

    fun issue(
        request: ListRequestsRequest,
        skip: Int,
    ): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString("$VERSION:${request.filterValue}:${request.pageSize}:$skip".toByteArray())

    /** The `skip` [request]'s token stands for: 0 for the first page. */
    fun skipOf(request: ListRequestsRequest): Int {
        if (request.pageToken.isEmpty()) return 0
        val token = decode(request.pageToken) ?: throw invalidArgument("page_token was not issued by this integration")
        if (token.filter != request.filterValue || token.pageSize != request.pageSize) {
            throw invalidArgument("page_token was issued for a different filter or page_size")
        }
        return token.skip
    }

    private class Decoded(
        val filter: Int,
        val pageSize: Int,
        val skip: Int,
    )

    /** Null for anything [issue] could not have written. */
    private fun decode(token: String): Decoded? {
        val fields =
            attempt { String(Base64.getUrlDecoder().decode(token)) }
                .getOrNull()
                ?.split(':')
                ?.takeIf { it.size == FIELD_COUNT && it[0] == VERSION }
                ?: return null
        val filter = fields[1].toIntOrNull() ?: return null
        val pageSize = fields[2].toIntOrNull() ?: return null
        val skip = fields[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return Decoded(filter, pageSize, skip)
    }
}
