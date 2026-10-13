package com.cooper.binge.companion.seerr.ui.blocklist

import com.binge.companion.sdk.paging.OffsetPage
import com.binge.companion.sdk.paging.OffsetPagingSource
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_TV
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrBlocklistEntryDto
import com.cooper.binge.companion.seerr.seerr.displayString
import com.cooper.binge.companion.seerr.seerr.toAvatarUrl
import com.cooper.binge.companion.seerr.seerr.toEpochMillisOrNull
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

const val BLOCKLIST_PAGE_SIZE = 20

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
            SEERR_MEDIA_TYPE_MOVIE -> RequestMediaType.Movie
            SEERR_MEDIA_TYPE_TV -> RequestMediaType.Tv
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
        addedByAvatarUrl = user?.avatar.toAvatarUrl(),
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
