package com.cooper.binge.companion.seerr.discover.title

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.toAvailability
import com.cooper.binge.companion.seerr.discover.toReleaseYear
import com.cooper.binge.companion.seerr.discover.toTitlePage
import com.cooper.binge.companion.seerr.seerr.FALLBACK_CERTIFICATION_REGION
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrTvDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** What the series page reads. It knows nothing of Retrofit, so a view model holds this and a test holds a fake. */
interface SeriesDetailsRepository {
    suspend fun series(tmdbId: Int): SeriesDetails

    /** Ratings, recommendations and similar shows. Each part that fails or that the server lacks is empty. */
    suspend fun extras(tmdbId: Int): TitleExtras
}

/**
 * [SeriesDetailsRepository] over the Seerr API. [serverUrl] is the server's address with its trailing slash, for the show's
 * page on it. [io] is the dispatcher the calls run on; it has no default.
 */
class SeerrSeriesRepository(
    private val api: suspend () -> SeerrApi,
    private val viewer: suspend () -> SeerrUserDto,
    private val profile: suspend () -> SeerrServerProfile,
    private val serverUrl: suspend () -> String,
    private val io: CoroutineDispatcher,
) : SeriesDetailsRepository {
    override suspend fun series(tmdbId: Int): SeriesDetails =
        withContext(io) {
            val api = api()
            val user = viewer()
            val profile = profile()
            val dto = api.tvFullDetails(tmdbId)
            val permissions = user.toPermissions()
            dto.toSeriesDetails(
                region = streamingRegion(api, user, profile),
                mayRequest = permissions.canRequestSeries,
                mayRequest4k = permissions.mayRequest4k(isTv = true, profile.settings),
                settings = profile.settings,
                webUrl = serverUrl() + "tv/" + tmdbId,
            )
        }

    /** Rotten Tomatoes only: the show route has no IMDb score, and it is served on every lineage. */
    override suspend fun extras(tmdbId: Int): TitleExtras =
        withContext(io) {
            coroutineScope {
                val api = api()
                val ratings = async { attempt { api.tvRatings(tmdbId) }.getOrNull() }
                val recommendations = async { attempt { api.tvRecommendations(tmdbId) }.getOrNull() }
                val similar = async { attempt { api.tvSimilar(tmdbId) }.getOrNull() }
                TitleExtras(
                    ratings =
                        ratings
                            .await()
                            ?.let { TitleRatings(it.criticsScore, it.audienceScore, imdbScore = null) }
                            ?.takeUnless { it.isEmpty },
                    recommendations =
                        recommendations
                            .await()
                            ?.toTitlePage(TitleKind.Series)
                            ?.results
                            .orEmpty(),
                    similar =
                        similar
                            .await()
                            ?.toTitlePage(TitleKind.Series)
                            ?.results
                            .orEmpty(),
                )
            }
        }
}

internal fun SeerrTvDetailsDto.toSeriesDetails(
    region: String?,
    mayRequest: Boolean,
    mayRequest4k: Boolean,
    settings: SeerrPublicSettings,
    webUrl: String,
): SeriesDetails {
    val seasons = seasonInfos(mediaInfo, settings.enableSpecialEpisodes, mayRequest4k)
    val crew = credits?.crew.orEmpty()
    val key = crew.filter { it.job in KEY_SERIES_CREW_JOBS }.toTitleCrew()
    val keyIds = key.map { it.personId }.toSet()
    return SeriesDetails(
        tmdbId = id,
        name = name,
        tagline = tagline?.takeIf { it.isNotBlank() },
        overview = overview?.takeIf { it.isNotBlank() },
        posterUrl = posterPath?.toTmdbPosterUrl(),
        backdropUrl = backdropPath?.toTmdbBackdropUrl(),
        firstAirYear = firstAirDate.toReleaseYear(),
        firstAirDate = firstAirDate?.takeIf { it.isNotBlank() },
        lastAirDate = lastAirDate?.takeIf { it.isNotBlank() },
        episodeRuntimeMinutes = episodeRunTime.firstOrNull { it > 0 },
        certification = certificationFor(region),
        voteAverage = voteAverage?.takeIf { it > 0.0 },
        voteCount = voteCount?.takeIf { it > 0 },
        genres = genres.map { it.name },
        keywords = keywords.map { it.name },
        status = status?.takeIf { it.isNotBlank() },
        inProduction = inProduction,
        originalLanguage = originalLanguage,
        spokenLanguageCodes = spokenLanguages.mapNotNull { it.code?.takeIf(String::isNotBlank) },
        networks = networks.map { it.name },
        creators = createdBy.map { it.name },
        seasonCount = numberOfSeasons?.takeIf { it > 0 },
        episodeCount = numberOfEpisodes?.takeIf { it > 0 },
        nextEpisode =
            nextEpisodeToAir?.let {
                NextEpisode(
                    it.seasonNumber,
                    it.episodeNumber,
                    it.name,
                    it.airDate?.takeIf(String::isNotBlank),
                )
            },
        seasons = seasons,
        cast = credits?.cast.orEmpty().toTitleCast(),
        keyCrew = key,
        otherCrew = crew.filter { it.id !in keyIds }.toTitleCrew(),
        videos = relatedVideos.toTitleVideos(),
        providers = watchProviders.providersFor(region),
        standard = VersionState(mediaInfo?.status.toAvailability(), mayRequest && seasons.any { it.standard == TitleAvailability.None }),
        fourK =
            if (mayRequest4k) {
                VersionState(mediaInfo?.status4k.toAvailability(), seasons.any { it.fourK == TitleAvailability.None })
            } else {
                null
            },
        partialRequests = settings.partialRequestsEnabled,
        mediaId = mediaInfo?.id,
        webUrl = webUrl,
    )
}

/**
 * The seasons the page lists: those with an episode, and the specials only where the server lists them, as the web client
 * filters. A season the server holds, or an open request covers, is not open; that is the server's own rule for dropping one.
 */
private fun SeerrTvDetailsDto.seasonInfos(
    media: SeerrMediaInfoDto?,
    specials: Boolean,
    with4k: Boolean,
): List<SeasonInfo> =
    seasons
        .filter { it.episodeCount > 0 && (specials || it.seasonNumber != 0) }
        .sortedBy { it.seasonNumber }
        .map { season ->
            SeasonInfo(
                number = season.seasonNumber,
                name = season.name?.takeIf { it.isNotBlank() } ?: "",
                episodeCount = season.episodeCount,
                airYear = season.airDate.toReleaseYear(),
                standard = media.seasonAvailability(season.seasonNumber, is4k = false),
                fourK = if (with4k) media.seasonAvailability(season.seasonNumber, is4k = true) else null,
            )
        }

/**
 * What the server holds of one season in one version: its own status when it has one, else Pending when an open request of
 * that version names the season, else None. A declined or completed request covers nothing.
 */
internal fun SeerrMediaInfoDto?.seasonAvailability(
    number: Int,
    is4k: Boolean,
): TitleAvailability {
    val info = this ?: return TitleAvailability.None
    val held =
        info.seasons
            .firstOrNull { it.seasonNumber == number }
            ?.let { if (is4k) it.status4k else it.status }
            .toAvailability()
    if (held != TitleAvailability.None) return held
    val requested =
        info.requests.any { request ->
            request.is4k == is4k &&
                request.status != SeerrRequestStatusCode.Declined &&
                request.status != SeerrRequestStatusCode.Completed &&
                request.seasons.any { it.seasonNumber == number }
        }
    return if (requested) TitleAvailability.Pending else TitleAvailability.None
}

private fun SeerrTvDetailsDto.certificationFor(region: String?): String? =
    listOfNotNull(region, FALLBACK_CERTIFICATION_REGION).firstNotNullOfOrNull { country ->
        contentRatings
            ?.results
            ?.firstOrNull { it.country == country }
            ?.rating
            ?.takeIf(String::isNotBlank)
    }

private val KEY_SERIES_CREW_JOBS = listOf("Executive Producer", "Director", "Writer", "Screenplay")
