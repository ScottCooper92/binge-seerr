package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.discover.seedMedia
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.TitleCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Newest entries read for a row before the hub's own kind is picked out of them: both kinds share one list. */
private const val MEDIA_WINDOW = 40

/** [HubMediaSource] over the Seerr API. A server without the media list has no Recently Added row, so it reads as empty. */
class SeerrHubMediaSource(
    private val api: suspend () -> SeerrApi,
    private val profile: suspend () -> SeerrServerProfile,
    private val titles: TitleCache,
    private val io: CoroutineDispatcher,
    private val serverUrl: suspend () -> String,
    private val statuses: TitleStatusStore,
) : HubMediaSource {
    override suspend fun serverUrl(): String = serverUrl.invoke()

    override suspend fun recentlyAdded(): List<SeerrRequestMediaDto> {
        if (!profile().hasRecentlyAdded) return emptyList()
        return withContext(io) {
            val read = statuses.beginRead()
            api().mediaList(filter = "allavailable", take = MEDIA_WINDOW, sort = "mediaAdded").results.also { statuses.seedMedia(read, it) }
        }
    }

    override suspend fun recentRequests(): List<SeerrRequestMediaDto> =
        withContext(io) {
            val read = statuses.beginRead()
            api().requests(take = MEDIA_WINDOW, sort = "added").results.map { it.media }.also { statuses.seedMedia(read, it) }
        }

    override suspend fun hydrate(
        mediaType: String,
        tmdbId: Int,
    ): HydratedTitle? = withContext(io) { titles.get(api(), mediaType, tmdbId) }
}
