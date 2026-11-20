package com.cooper.binge.companion.seerr.data

import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.ui.requests.REQUESTS_PAGE_SIZE

/** What one list asks the server for, and the cache key its pages are stored under. */
data class RequestListQuery(
    val filter: String,
    val sort: String,
    val requestedBy: Int?,
) {
    /** The scope is in the key so rows loaded for one viewer's permissions never read as another's. */
    val listKey: String get() = "$filter:$sort:${requestedBy ?: "all"}"
}

/**
 * Pages `GET request` for one [query] into the cache. Every open refreshes, so the rows on screen
 * are at most one page stale; further pages append off the cursor the store keeps. Rows are titled
 * concurrently, and one whose media the app cannot show is dropped rather than failing the page.
 */
class RequestsRemoteMediator(
    private val query: RequestListQuery,
    private val api: suspend () -> SeerrApi,
    store: RequestStore,
    onRefresh: (rowsWritten: Int?) -> Unit = {},
    /** Told what each page says of its titles, so the list's read is ordered against actions made while it was in flight. */
    private val statuses: TitleStatusStore = TitleStatusStore(),
    /** One row from one request, titled through the API; null for a request the app cannot show. */
    private val toEntity: suspend (SeerrRequestDto, SeerrApi, String, Int) -> RequestEntity?,
) : OffsetRemoteMediator<RequestEntity>(
        listKey = query.listKey,
        pageSize = REQUESTS_PAGE_SIZE,
        nextSkip = store::nextSkip,
        refresh = store::refresh,
        append = store::append,
        onRefresh = onRefresh,
    ) {
    override suspend fun fetch(skip: Int): FetchedPage<RequestEntity> {
        val api = api()
        val read = statuses.beginRead()
        val page =
            api.requests(
                take = REQUESTS_PAGE_SIZE,
                skip = skip,
                filter = query.filter,
                sort = query.sort,
                requestedBy = query.requestedBy,
            )
        page.results.forEach { seed(it.media, read) }
        return FetchedPage(
            page.results.toRowsConcurrently(skip) { dto, index -> toEntity(dto, api, query.listKey, index) },
            page.pageInfo.pages,
        )
    }

    private fun seed(
        media: SeerrRequestMediaDto,
        read: Long,
    ) {
        val type =
            when (media.mediaType) {
                "movie" -> TitleType.Movie
                "tv" -> TitleType.Tv
                else -> return
            }
        if (media.status != null || media.status4k != null) {
            statuses.seed(TitleKey(type, media.tmdbId), TitleStatuses(media.status, media.status4k, media.id), read)
        }
    }
}
