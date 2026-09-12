package io.github.scottcooper92.binge.seerr.data

/** Where a `take`/`skip` list stands after one page: done, or the `skip` of the next. */
data class PageCursor(
    val endReached: Boolean,
    val nextSkip: Int?,
)

/** A server that sends no page count is taken to have sent its last page. */
fun pageCursorAfter(
    skip: Int,
    pageSize: Int,
    totalPages: Int?,
): PageCursor {
    val pages = totalPages?.takeIf { it > 0 } ?: (skip / pageSize + 1)
    val endReached = skip / pageSize + 1 >= pages
    return PageCursor(endReached = endReached, nextSkip = if (endReached) null else skip + pageSize)
}
