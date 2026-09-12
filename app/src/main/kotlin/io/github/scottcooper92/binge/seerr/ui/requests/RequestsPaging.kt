package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.scottcooper92.binge.seerr.seerr.HydratedTitle
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestUserDto
import io.github.scottcooper92.binge.seerr.seerr.etaMinutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.OffsetDateTime

const val REQUESTS_PAGE_SIZE = 20
private const val MEDIA_TYPE_MOVIE = "movie"
private const val MEDIA_TYPE_TV = "tv"
private const val STATUS_DOWNLOADING = "downloading"

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
        return try {
            val result = loadPage(take = pageSize, skip = page * pageSize)
            val totalPages = result.totalPages ?: (page + 1)
            LoadResult.Page(
                data = result.items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (page + 1 >= totalPages) null else page + 1,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    final override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1) ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}

/**
 * Pages `GET request` for one [filter] in one [sort], each row titled through [hydrate]
 * concurrently; a failed lookup degrades its row rather than the page. [requestedBy] narrows the
 * list to one user's requests, for a user who may not see everyone's.
 */
class RequestsPagingSource(
    private val api: suspend () -> SeerrApi,
    private val filter: RequestFilter,
    private val sort: RequestSort,
    private val requestedBy: Int?,
    private val hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
    private val now: () -> Long = System::currentTimeMillis,
) : OffsetPagingSource<RequestItem>(REQUESTS_PAGE_SIZE) {
    override suspend fun loadPage(
        take: Int,
        skip: Int,
    ): OffsetPage<RequestItem> {
        val api = api()
        val page = api.requests(take = take, skip = skip, filter = filter.apiValue, sort = sort.apiValue, requestedBy = requestedBy)
        val items =
            coroutineScope {
                page.results
                    .map { dto -> async { dto.toRequestItem(api, hydrate, now()) } }
                    .awaitAll()
                    .filterNotNull()
            }
        return OffsetPage(items, page.pageInfo.pages.takeIf { it > 0 })
    }
}

/** Null for a request whose media type this app does not render; there is nothing to show. */
suspend fun SeerrRequestDto.toRequestItem(
    api: SeerrApi,
    hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
    nowMillis: Long,
): RequestItem? {
    val mediaType =
        when (media.mediaType) {
            MEDIA_TYPE_MOVIE -> RequestMediaType.Movie
            MEDIA_TYPE_TV -> RequestMediaType.Tv
            else -> return null
        }
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
        requestedAtMillis = createdAt?.toEpochMillisOrNull(),
        status = status,
        mediaStatus = media.status,
        download = statuses.toDownload(nowMillis),
        seasonNumbers = seasons.map { it.seasonNumber },
        is4k = is4k,
    )
}

private fun List<io.github.scottcooper92.binge.seerr.seerr.SeerrDownloadStatusDto>.toDownload(nowMillis: Long): RequestDownload? {
    if (isEmpty()) return null
    val totalSize = sumOf { it.size ?: 0.0 }
    val totalLeft = sumOf { it.sizeLeft ?: 0.0 }
    val fraction = if (totalSize > 0.0) ((totalSize - totalLeft) / totalSize).toFloat().coerceIn(0f, 1f) else 0f
    val downloading =
        when {
            any { it.status?.equals(STATUS_DOWNLOADING, ignoreCase = true) == true } -> true
            any { it.status != null } -> false
            else -> fraction > 0f
        }
    return RequestDownload(fraction = fraction, etaMinutes = etaMinutes(nowMillis), downloading = downloading)
}

/** Email is a last resort and masked to its local part. */
private fun SeerrRequestUserDto.displayString(): String? =
    listOfNotNull(displayName, username).firstOrNull { it.isNotBlank() } ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }

private fun String.toEpochMillisOrNull(): Long? =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
