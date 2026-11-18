package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult

/** A person on a title: [role] is a character for cast and a job for crew. [photoUrl] is a full URL. */
data class TitlePerson(
    val personId: Int,
    val name: String,
    val role: String?,
    val photoUrl: String?,
)

/** A clip the server lists for a title. [isTrailer] is the server's own type; the list puts trailers first. */
data class TitleVideo(
    val name: String,
    val url: String,
    val isTrailer: Boolean = false,
)

/** The address of the first trailer, or null where the server lists none; another kind of clip is not a trailer. */
fun List<TitleVideo>.trailerUrl(): String? = firstOrNull { it.isTrailer }?.url

data class TitleProvider(
    val id: Int,
    val name: String,
    val logoUrl: String?,
)

/** Where the title streams or can be bought in one region. [link] is the server's TMDB page for it, where it gave one. */
data class TitleProviders(
    val region: String,
    val link: String?,
    val streaming: List<TitleProvider>,
    val buy: List<TitleProvider>,
) {
    val isEmpty: Boolean get() = streaming.isEmpty() && buy.isEmpty()
}

data class TitleCollection(
    val id: Int,
    val name: String,
    val posterUrl: String?,
)

/** Rotten Tomatoes and IMDb scores, each as the server gave it. Absent where the server has neither. */
data class TitleRatings(
    val criticsPercent: Int?,
    val audiencePercent: Int?,
    val imdbScore: Double?,
) {
    val isEmpty: Boolean get() = criticsPercent == null && audiencePercent == null && imdbScore == null
}

/** One version of a title, standard or 4K: what the server holds of it and whether this user may ask for it. */
data class VersionState(
    val availability: TitleAvailability,
    val mayRequest: Boolean,
)

/**
 * Everything the film page shows, from the server's own answer. It names no Retrofit or Android type, so a view model
 * holds it and a test builds it. [cast] is in the server's billing order; [keyCrew] is the director, writers and
 * producers, once each; [otherCrew] is everyone else, for "See all".
 */
data class MovieDetails(
    val tmdbId: Int,
    val title: String,
    val tagline: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String?,
    val runtimeMinutes: Int?,
    val certification: String?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val genres: List<String>,
    val keywords: List<String>,
    val status: String?,
    val originalLanguage: String?,
    val spokenLanguageCodes: List<String>,
    val studios: List<String>,
    val budget: Long?,
    val revenue: Long?,
    val cast: List<TitlePerson>,
    val keyCrew: List<TitlePerson>,
    val otherCrew: List<TitlePerson>,
    val videos: List<TitleVideo>,
    val providers: TitleProviders?,
    val collection: TitleCollection?,
    val standard: VersionState,
    val fourK: VersionState?,
    val mediaId: Int?,
    val webUrl: String,
    val releaseDate: String? = null,
)

/** The sections that load apart from the page, so one that fails leaves only itself out. */
data class TitleExtras(
    val ratings: TitleRatings?,
    val recommendations: List<TitleResult>,
    val similar: List<TitleResult>,
) {
    /** The server's recommendations, then its similar titles, each title once: one rail, as the page shows it. */
    val moreLikeThis: List<TitleResult> get() = (recommendations + similar).distinctBy { it.kind to it.tmdbId }
}

/** What the film page reads. It knows nothing of Retrofit, so a view model holds this and a test holds a fake. */
interface TitleDetailsRepository {
    suspend fun movie(tmdbId: Int): MovieDetails

    /** The films of a collection, in release order. Any signed-in user may read them; a failed read throws. */
    suspend fun collectionParts(collectionId: Int): List<TitleResult>

    /** Ratings, recommendations and similar titles. Each part that fails or that the server lacks is empty. */
    suspend fun extras(
        kind: TitleKind,
        tmdbId: Int,
    ): TitleExtras
}
