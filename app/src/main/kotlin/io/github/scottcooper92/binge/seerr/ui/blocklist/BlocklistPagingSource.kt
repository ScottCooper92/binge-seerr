package io.github.scottcooper92.binge.seerr.ui.blocklist

import io.github.scottcooper92.binge.seerr.seerr.HydratedTitle
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrBlocklistEntryDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestUserDto
import io.github.scottcooper92.binge.seerr.ui.issues.toEpochMillisOrNull
import io.github.scottcooper92.binge.seerr.ui.requests.OffsetPage
import io.github.scottcooper92.binge.seerr.ui.requests.OffsetPagingSource
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

const val BLOCKLIST_PAGE_SIZE = 20
private const val MEDIA_TYPE_MOVIE = "movie"
private const val MEDIA_TYPE_TV = "tv"

/**
 * Pages the blocklist at [path] for one [filter] and an optional [search], each row titled
 * through [hydrate] concurrently. A failed lookup keeps the title the blocker gave; an entry
 * with no media, or one of a type this app does not render, is dropped rather than failing the page.
 */
class BlocklistPagingSource(
    private val api: suspend () -> SeerrApi,
    private val path: String,
    private val filter: BlocklistFilter,
    private val search: String?,
    private val hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
) : OffsetPagingSource<BlocklistItem>(BLOCKLIST_PAGE_SIZE) {
    override suspend fun loadPage(
        take: Int,
        skip: Int,
    ): OffsetPage<BlocklistItem> {
        val api = api()
        val page =
            api.blocklist(
                path = path,
                take = take,
                skip = skip,
                filter = filter.apiValue,
                search = search?.takeIf { it.isNotBlank() },
            )
        val items =
            coroutineScope {
                page.results
                    .map { dto -> async { dto.toBlocklistItem(api, hydrate) } }
                    .awaitAll()
                    .filterNotNull()
            }
        return OffsetPage(items, page.pageInfo.pages.takeIf { it > 0 })
    }
}

internal suspend fun SeerrBlocklistEntryDto.toBlocklistItem(
    api: SeerrApi,
    hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
): BlocklistItem? {
    val tmdbId = tmdbId ?: return null
    val rawType = mediaType ?: return null
    val type =
        when (rawType) {
            MEDIA_TYPE_MOVIE -> RequestMediaType.Movie
            MEDIA_TYPE_TV -> RequestMediaType.Tv
            else -> return null
        }
    val details = hydrate(api, rawType, tmdbId)
    return BlocklistItem(
        id = id,
        tmdbId = tmdbId,
        mediaType = type,
        title = details?.title ?: title?.takeIf { it.isNotBlank() },
        posterUrl = details?.posterUrl,
        year = details?.year,
        addedBy = user?.displayString(),
        addedAtMillis = createdAt?.toEpochMillisOrNull(),
        tags = blocklistedTags.toBlocklistTags(),
    )
}

/** The server's comma-separated tag list, as trimmed labels. */
internal fun String?.toBlocklistTags(): List<String> =
    this
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

/** Email is a last resort and masked to its local part, as a requester's is. */
private fun SeerrRequestUserDto.displayString(): String? =
    listOfNotNull(displayName, username).firstOrNull { it.isNotBlank() } ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
