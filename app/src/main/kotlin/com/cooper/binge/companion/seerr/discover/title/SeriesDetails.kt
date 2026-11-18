package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.request.SeasonChoice
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices

/** One season of a show. [standard] and [fourK] are what the server holds or has been asked for, per version. */
data class SeasonInfo(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val airYear: String?,
    val standard: TitleAvailability,
    val fourK: TitleAvailability?,
)

/** The next episode the server knows of, where it returned one. */
data class NextEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val airDate: String?,
)

/**
 * Everything the series page shows, from the server's own answer. It names no Retrofit or Android type, so a view model holds
 * it and a test builds it. [seasons] has season 0 only where the server lists specials, and none that has no episodes yet.
 * [partialRequests] is the server's setting: off, a request is for every season still open.
 */
data class SeriesDetails(
    val tmdbId: Int,
    val name: String,
    val tagline: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val firstAirYear: String?,
    val firstAirDate: String?,
    val lastAirDate: String?,
    val episodeRuntimeMinutes: Int?,
    val certification: String?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val genres: List<String>,
    val keywords: List<String>,
    val status: String?,
    val inProduction: Boolean,
    val originalLanguage: String?,
    val spokenLanguageCodes: List<String>,
    val networks: List<String>,
    val creators: List<String>,
    val seasonCount: Int?,
    val episodeCount: Int?,
    val nextEpisode: NextEpisode?,
    val seasons: List<SeasonInfo>,
    val cast: List<TitlePerson>,
    val keyCrew: List<TitlePerson>,
    val otherCrew: List<TitlePerson>,
    val videos: List<TitleVideo>,
    val providers: TitleProviders?,
    val standard: VersionState,
    val fourK: VersionState?,
    val partialRequests: Boolean,
    val mediaId: Int?,
    val webUrl: String,
) {
    /** The seasons of each version for the request sheet. */
    val seasonChoices: SeasonChoices
        get() =
            SeasonChoices(
                standard = seasons.map { SeasonChoice(it.number, it.name, it.episodeCount, it.standard) },
                fourK = seasons.map { SeasonChoice(it.number, it.name, it.episodeCount, it.fourK ?: TitleAvailability.None) },
            )
}

/** One episode of a season. [airDate] is a `yyyy-mm-dd` date and null for one the server has none for; [stillUrl] is a full URL. */
data class SeasonEpisode(
    val number: Int,
    val name: String,
    val overview: String?,
    val airDate: String?,
    val stillUrl: String?,
    val rating: Float?,
)
