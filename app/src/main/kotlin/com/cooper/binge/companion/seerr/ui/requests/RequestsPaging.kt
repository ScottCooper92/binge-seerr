package com.cooper.binge.companion.seerr.ui.requests

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrDownloadStatusDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestsPageDto
import com.cooper.binge.companion.seerr.seerr.displayString
import com.cooper.binge.companion.seerr.seerr.downloadFraction
import com.cooper.binge.companion.seerr.seerr.etaMinutes
import com.cooper.binge.companion.seerr.seerr.isDownloading
import com.cooper.binge.companion.seerr.seerr.toAvatarUrl
import com.cooper.binge.companion.seerr.seerr.toEpochMillisOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

const val REQUESTS_PAGE_SIZE = 20

/** One fetched page; [totalPages] is null when the server sends no `pageInfo`, which reads as the last page. */
data class OffsetPage<T>(
    val items: List<T>,
    val totalPages: Int?,
)

/**
 * The shape every Seerr list shares: a `take`/`skip` endpoint paged by integer page index. The
 * base owns the key arithmetic and turns any failure into a load error the list can retry from;
 * a subclass supplies one page.
 */
abstract class OffsetPagingSource<T : Any>(
    private val pageSize: Int,
) : PagingSource<Int, T>() {
    protected abstract suspend fun loadPage(
        take: Int,
        skip: Int,
    ): OffsetPage<T>

    final override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 0
        return attempt {
            val result = loadPage(take = pageSize, skip = page * pageSize)
            val totalPages = result.totalPages ?: (page + 1)
            LoadResult.Page(
                data = result.items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (page + 1 >= totalPages) null else page + 1,
            )
        }.getOrElse { failure -> LoadResult.Error(failure) }
    }

    final override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1) ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}

/**
 * A page of requests, titled: every row hydrated concurrently, a failed lookup degrading its own
 * row rather than the page. Shared because the browser and one user's page read different
 * endpoints — `GET request` and `GET user/{id}/requests`, which need different permissions — and
 * the same thing of whichever answered.
 */
internal suspend fun SeerrRequestsPageDto.toRequestPage(
    api: SeerrApi,
    hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
    nowMillis: Long,
): OffsetPage<RequestItem> =
    OffsetPage(
        items =
            coroutineScope {
                results
                    .map { dto -> async { dto.toRequestItem(api, hydrate, nowMillis) } }
                    .awaitAll()
                    .filterNotNull()
            },
        totalPages = pageInfo.pages.takeIf { it > 0 },
    )

/** Null for a request whose media type this app does not render; there is nothing to show. */
suspend fun SeerrRequestDto.toRequestItem(
    api: SeerrApi,
    hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
    nowMillis: Long,
): RequestItem? {
    val mediaType = media.mediaType.toRequestMediaTypeOrNull() ?: return null
    val details = hydrate(api, media.mediaType, media.tmdbId)
    val statuses = if (is4k) media.downloadStatus4k else media.downloadStatus
    return RequestItem(
        id = id,
        tmdbId = media.tmdbId,
        mediaType = mediaType,
        title = details?.title,
        posterUrl = details?.posterUrl,
        year = details?.year,
        requestedBy = requestedBy?.displayString(),
        requestedById = requestedBy?.id,
        requestedByAvatarUrl = requestedBy?.avatar.toAvatarUrl(),
        requestedAtMillis = createdAt?.toEpochMillisOrNull(),
        status = status,
        mediaStatus = if (is4k) media.status4k else media.status,
        download = statuses.toDownload(nowMillis),
        seasonNumbers = seasons.map { it.seasonNumber },
        is4k = is4k,
        backdropUrl = details?.backdropUrl,
        overview = details?.overview,
        certification = details?.certification,
    )
}

private fun List<SeerrDownloadStatusDto>.toDownload(nowMillis: Long): RequestDownload? {
    if (isEmpty()) return null
    val fraction = downloadFraction()
    return RequestDownload(fraction = fraction, etaMinutes = etaMinutes(nowMillis), downloading = isDownloading(fraction))
}
