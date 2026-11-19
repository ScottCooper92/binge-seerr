package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.discover.title.toRatings
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [HubScoreSource] over the Seerr API: the title page's own scores. A film's are Rotten Tomatoes and IMDb in one call, where the
 * server has it; a show's are Rotten Tomatoes only, which every lineage serves. A title with neither reads as none.
 */
class SeerrHubScoreSource(
    private val api: suspend () -> SeerrApi,
    private val profile: suspend () -> SeerrServerProfile,
    private val io: CoroutineDispatcher,
) : HubScoreSource {
    override suspend fun ratings(
        kind: TitleKind,
        tmdbId: Int,
    ): TitleRatings? =
        withContext(io) {
            when (kind) {
                TitleKind.Movie -> if (profile().hasCombinedRatings) api().movieRatings(tmdbId).toRatings() else null
                TitleKind.Series -> api().tvRatings(tmdbId).let { TitleRatings(it.criticsScore, it.audienceScore, imdbScore = null) }
                TitleKind.Person, TitleKind.Collection -> null
            }?.takeUnless { it.isEmpty }
        }
}
