package com.cooper.binge.companion.seerr.discover.title

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.toAvailability
import com.cooper.binge.companion.seerr.discover.toReleaseYear
import com.cooper.binge.companion.seerr.discover.toTitlePage
import com.cooper.binge.companion.seerr.discover.toTitleResult
import com.cooper.binge.companion.seerr.seerr.FALLBACK_CERTIFICATION_REGION
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrMovieDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrMovieWatchProviderDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrVideoDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchRegionDto
import com.cooper.binge.companion.seerr.seerr.certificationIn
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private const val MAX_VIDEOS = 3
private val KEY_CREW_JOBS = listOf("Director", "Writer", "Screenplay", "Producer")

/**
 * [TitleDetailsRepository] over the Seerr API. [serverUrl] is the server's address with its trailing slash, for the
 * title's page on it. [io] is the dispatcher the calls run on; it has no default.
 */
class SeerrTitleDetailsRepository(
    private val api: suspend () -> SeerrApi,
    private val viewer: suspend () -> SeerrUserDto,
    private val profile: suspend () -> SeerrServerProfile,
    private val serverUrl: suspend () -> String,
    private val io: CoroutineDispatcher,
) : TitleDetailsRepository {
    override suspend fun movie(tmdbId: Int): MovieDetails =
        withContext(io) {
            val api = api()
            val user = viewer()
            val profile = profile()
            val dto = api.movieFullDetails(tmdbId)
            val region = streamingRegion(api, user, profile)
            val permissions = user.toPermissions()
            dto
                .toMovieDetails(
                    region = region,
                    mayRequest = permissions.canRequestMovie,
                    mayRequest4k = permissions.mayRequest4k(isTv = false, profile.settings),
                    webUrl = serverUrl() + "movie/" + tmdbId,
                ).copy(companiesOpen = profile.hasDiscoverByCategory)
        }

    override suspend fun collectionParts(collectionId: Int): List<TitleResult> =
        withContext(io) { api().collection(collectionId).parts.mapNotNull { it.toTitleResult(TitleKind.Movie) } }

    override suspend fun extras(
        kind: TitleKind,
        tmdbId: Int,
    ): TitleExtras =
        withContext(io) {
            coroutineScope {
                val api = api()
                val profile = profile()
                val ratings = async { if (profile.hasCombinedRatings) attempt { api.movieRatings(tmdbId) }.getOrNull() else null }
                val recommendations = async { attempt { api.movieRecommendations(tmdbId) }.getOrNull() }
                val similar = async { attempt { api.movieSimilar(tmdbId) }.getOrNull() }
                TitleExtras(
                    ratings = ratings.await()?.toRatings()?.takeUnless { it.isEmpty },
                    recommendations =
                        recommendations
                            .await()
                            ?.toTitlePage(TitleKind.Movie)
                            ?.results
                            .orEmpty(),
                    similar =
                        similar
                            .await()
                            ?.toTitlePage(TitleKind.Movie)
                            ?.results
                            .orEmpty(),
                )
            }
        }
}

internal fun SeerrMovieDetailsDto.toMovieDetails(
    region: String?,
    mayRequest: Boolean,
    mayRequest4k: Boolean,
    webUrl: String,
): MovieDetails {
    val crew = credits?.crew.orEmpty()
    val key =
        crew
            .filter { it.job in KEY_CREW_JOBS }
            .distinctBy { it.id }
            .map { person ->
                // A person who holds several key jobs reads once, under all of them.
                val jobs = crew.filter { it.id == person.id && it.job in KEY_CREW_JOBS }.mapNotNull { it.job }.distinct()
                TitlePerson(person.id, person.name, jobs.joinToString(", ").ifEmpty { null }, person.profilePath?.toTmdbPosterUrl())
            }
    val keyIds = key.map { it.personId }.toSet()
    val others = crew.filter { it.id !in keyIds }.toTitleCrew()
    val standardStatus = mediaInfo?.status.toAvailability()
    val status4k = mediaInfo?.status4k.toAvailability()
    return MovieDetails(
        tmdbId = id,
        title = title,
        tagline = tagline?.takeIf { it.isNotBlank() },
        overview = overview?.takeIf { it.isNotBlank() },
        posterUrl = posterPath?.toTmdbPosterUrl(),
        backdropUrl = backdropPath?.toTmdbBackdropUrl(),
        year = releaseDate.toReleaseYear(),
        runtimeMinutes = runtime?.takeIf { it > 0 },
        certification = certificationFor(region),
        voteAverage = voteAverage?.takeIf { it > 0.0 },
        voteCount = voteCount?.takeIf { it > 0 },
        genres = genres.map { it.name },
        keywords = keywords.map { it.name },
        status = status?.takeIf { it.isNotBlank() },
        originalLanguage = originalLanguage,
        spokenLanguageCodes = spokenLanguages.mapNotNull { it.code?.takeIf(String::isNotBlank) },
        studios = productionCompanies.map { TitleCompany(it.id, it.name) },
        budget = budget?.takeIf { it > 0 },
        revenue = revenue?.takeIf { it > 0 },
        cast =
            credits?.cast.orEmpty().toTitleCast(),
        keyCrew = key,
        otherCrew = others,
        videos = relatedVideos.toTitleVideos(),
        providers = watchProviders.providersFor(region),
        collection = collection?.let { TitleCollection(it.id, it.name, it.posterPath?.toTmdbPosterUrl()) },
        standard = VersionState(standardStatus, mayRequest && standardStatus.mayBeRequested()),
        fourK = if (mayRequest4k) VersionState(status4k, status4k.mayBeRequested()) else null,
        mediaId = mediaInfo?.id,
        webUrl = webUrl,
        releaseDate = releaseDate?.takeIf { it.isNotBlank() },
    )
}

/** Only a title the server holds nothing of can be requested again; a declined or removed one reads as None here. */
private fun com.cooper.binge.companion.seerr.discover.TitleAvailability.mayBeRequested() =
    this == com.cooper.binge.companion.seerr.discover.TitleAvailability.None

private fun SeerrMovieDetailsDto.certificationFor(region: String?): String? =
    listOfNotNull(region, FALLBACK_CERTIFICATION_REGION).firstNotNullOfOrNull { releases.certificationIn(it) }

internal fun List<SeerrWatchRegionDto>.providersFor(region: String?): TitleProviders? {
    val match = firstOrNull { it.region == region } ?: return null
    return TitleProviders(match.region, match.link, match.flatrate.toProviders(), match.buy.toProviders()).takeUnless { it.isEmpty }
}

private fun List<SeerrMovieWatchProviderDto>.toProviders() =
    sortedBy { it.displayPriority ?: Int.MAX_VALUE }.map { TitleProvider(it.id, it.name, it.logoPath?.toTmdbPosterUrl()) }

private fun com.cooper.binge.companion.seerr.seerr.SeerrRatingsDto.toRatings() =
    TitleRatings(rottenTomatoes?.criticsScore, rottenTomatoes?.audienceScore, imdb?.criticsScore)

/** The server lists every clip a title has, often repeated: each once, trailers first, and only a few of them. */
internal fun List<SeerrVideoDto>.toTitleVideos(): List<TitleVideo> =
    filter { it.site == null || it.site == "YouTube" }
        .distinctBy { it.key }
        .sortedBy { if (it.type == "Trailer") 0 else 1 }
        .mapNotNull { video -> video.url?.let { TitleVideo(video.name, it, isTrailer = video.type == "Trailer") } }
        .take(MAX_VIDEOS)

/**
 * The region the title's streaming providers are read for: the user's own streaming region, then the server's, then the
 * discover regions in the same order, as the web client reads them. A server that has one region only has no streaming one.
 */
internal suspend fun streamingRegion(
    api: SeerrApi,
    user: SeerrUserDto,
    profile: SeerrServerProfile,
): String? {
    val own = attempt { api.userMainSettings(user.id) }.getOrNull()
    val server = profile.settings
    val streaming = if (profile.hasStreamingRegion) listOf(own?.streamingRegion, server.streamingRegion) else emptyList()
    val discover = listOf(own?.discoverRegion ?: own?.region, server.discoverRegion ?: server.region)
    return (streaming + discover).firstOrNull { !it.isNullOrBlank() }
}
