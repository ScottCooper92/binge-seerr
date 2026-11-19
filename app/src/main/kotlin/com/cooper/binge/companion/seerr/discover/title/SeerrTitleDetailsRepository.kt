package com.cooper.binge.companion.seerr.discover.title

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.data.toTitleStatuses
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.collection.CollectionRepository
import com.cooper.binge.companion.seerr.discover.toAvailability
import com.cooper.binge.companion.seerr.discover.toReleaseYear
import com.cooper.binge.companion.seerr.discover.toTitlePage
import com.cooper.binge.companion.seerr.seerr.FALLBACK_CERTIFICATION_REGION
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
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
import com.cooper.binge.companion.seerr.ui.isOpenableSite
import com.cooper.binge.companion.seerr.ui.requests.mediaServerName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private const val MAX_VIDEOS = 3
private val KEY_CREW_JOBS = listOf("Director", "Writer", "Screenplay", "Producer")

/**
 * [TitleDetailsRepository] over the Seerr API. [serverUrl] is the server's address with its trailing slash, for the
 * title's page on it. [deviceRegion] is the country the phone is set to, an ISO 3166-1 code, or null. [io] is the
 * dispatcher the calls run on; it has no default. A collection's films are read through [collections], the one place that
 * reads and orders them.
 */
class SeerrTitleDetailsRepository(
    private val api: suspend () -> SeerrApi,
    private val viewer: suspend () -> SeerrUserDto,
    private val profile: suspend () -> SeerrServerProfile,
    private val serverUrl: suspend () -> String,
    private val deviceRegion: () -> String?,
    private val collections: CollectionRepository,
    private val io: CoroutineDispatcher,
    private val statuses: TitleStatusStore = TitleStatusStore(),
) : TitleDetailsRepository {
    override suspend fun movie(tmdbId: Int): MovieDetails =
        withContext(io) {
            val api = api()
            val user = viewer()
            val profile = profile()
            val read = statuses.beginRead()
            val dto = api.movieFullDetails(tmdbId)
            statuses.seed(TitleKey(TitleType.Movie, tmdbId), dto.mediaInfo.toTitleStatuses(), read)
            val region = streamingRegion(api, user, profile, deviceRegion())
            val permissions = user.toPermissions()
            dto
                .toMovieDetails(
                    region = region,
                    mayRequest = permissions.canRequestMovie,
                    mayRequest4k = permissions.mayRequest4k(isTv = false, profile.settings),
                    viewerId = user.id,
                    mayViewAllRequests = permissions.canViewRequests,
                    webUrl = serverUrl() + "movie/" + tmdbId,
                ).copy(
                    companiesOpen = profile.hasDiscoverByCategory,
                    mediaServerName = profile.mediaServerName(),
                    canRequest = permissions.canRequestMovie,
                    keywordsOpen = profile.hasKeywordDiscover,
                    mayReport = profile.hasIssues && permissions.canReportIssues,
                    mayUnblock = profile.hasBlocklist && permissions.canManageBlocklist,
                    onWatchlist = dto.onUserWatchlist == true,
                    mayWatchlist = profile.hasWatchlistEdit,
                )
        }

    override suspend fun collectionParts(collectionId: Int): List<TitleResult> = collections.collection(collectionId).parts

    override suspend fun setWatchlist(
        tmdbId: Int,
        title: String,
        onList: Boolean,
    ) = withContext(io) { setOnWatchlist(api(), tmdbId, title, "movie", onList) }

    override suspend fun unblock(tmdbId: Int) =
        withContext(io) { statuses.unblocking(TitleKey(TitleType.Movie, tmdbId)) { unblockTitle(api(), profile(), tmdbId, "movie") } }

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
    viewerId: Int,
    mayViewAllRequests: Boolean,
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
        keywords = keywords.map { TitleKeyword(it.id, it.name) },
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
        watchUrl = mediaInfo?.watchUrl(),
        webUrl = webUrl,
        releaseDate = releaseDate?.takeIf { it.isNotBlank() },
        requests =
            mediaInfo
                ?.requests
                .orEmpty()
                .viewableBy(viewerId, mayViewAllRequests)
                .map { it.toTitleRequest() },
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

/** A show's Rotten Tomatoes alone: the show route has no IMDb score. */
internal fun com.cooper.binge.companion.seerr.seerr.SeerrRottenTomatoesDto.toRatings() =
    TitleRatings(criticsScore, audienceScore, imdbScore = null)

internal fun com.cooper.binge.companion.seerr.seerr.SeerrRatingsDto.toRatings() =
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
 * Seerr's default is no region anywhere, so the last resort is [deviceRegion], the country the phone is set to, and a
 * region someone chose always wins over it.
 */
internal suspend fun streamingRegion(
    api: SeerrApi,
    user: SeerrUserDto,
    profile: SeerrServerProfile,
    deviceRegion: String?,
): String? {
    val own = attempt { api.userMainSettings(user.id) }.getOrNull()
    val server = profile.settings
    val streaming = if (profile.hasStreamingRegion) listOf(own?.streamingRegion, server.streamingRegion) else emptyList()
    val discover = listOf(own?.discoverRegion ?: own?.region, server.discoverRegion ?: server.region)
    return (streaming + discover + deviceRegion).firstOrNull { !it.isNullOrBlank() }
}

/**
 * The requests this viewer may open. The title's answer lists everyone's, but `GET /request/{id}` refuses another user's to a
 * viewer who holds neither Manage Requests nor Request View, so those are dropped here rather than offered as a tap that fails.
 */
internal fun List<com.cooper.binge.companion.seerr.seerr.SeerrRequestSummaryDto>.viewableBy(
    viewerId: Int,
    mayViewAll: Boolean,
) = if (mayViewAll) this else filter { it.requestedBy?.id == viewerId }

/** The request the title's own answer lists, reduced to what the page keeps: its id, version, state and who asked. */
internal fun com.cooper.binge.companion.seerr.seerr.SeerrRequestSummaryDto.toTitleRequest() =
    TitleRequest(
        id = id,
        is4k = is4k,
        status = status,
        requesterId = requestedBy?.id,
    )

/**
 * The title's link in the media server's web UI: the first the server filled in, the standard version's before the 4K one's, so a title
 * held only in 4K still opens. Null where that first link is not a real site.
 */
internal fun com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto.watchUrl(): String? =
    listOf(mediaUrl, jellyfinMediaUrl, plexUrl, mediaUrl4k, plexUrl4k).firstOrNull { !it.isNullOrBlank() }?.takeIf(::isOpenableSite)

/** Takes a title off the blocklist, by the path and the media type this server's version wants. */
internal suspend fun unblockTitle(
    api: SeerrApi,
    profile: SeerrServerProfile,
    tmdbId: Int,
    mediaType: String,
) = api.removeFromBlocklist(profile.blocklistPath, tmdbId, profile.unblockMediaType(mediaType))

/** Shows the title unblocked at once, and puts the block back if the server refuses. */
internal suspend fun TitleStatusStore.unblocking(
    key: TitleKey,
    unblock: suspend () -> Unit,
) {
    val undo = applyOptimistic(key) { held -> held.copy(standard = held.standard.unlessBlocked(), fourK = held.fourK.unlessBlocked()) }
    attempt { unblock() }.onFailure { rollback(undo) }.getOrThrow()
}

private fun SeerrMediaStatusCode?.unlessBlocked(): SeerrMediaStatusCode? = takeUnless { it == SeerrMediaStatusCode.Blocklisted }
