package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile

/** What a row of a title list is. A person and a collection come back from search and trending next to titles. */
enum class TitleKind { Movie, Series, Person, Collection }

/** What the server says about a title in a library and its requests, as far as a card shows it. */
enum class TitleAvailability { None, Pending, Processing, PartiallyAvailable, Available, Blocklisted }

/**
 * One row of any title list: a hub row, the grid, search. [artworkPath] is a TMDB path (a poster, or a person's photo),
 * not a URL. [mediaId] is the server's own id for a title it already holds, which a request or issue needs.
 */
data class TitleResult(
    val kind: TitleKind,
    val tmdbId: Int,
    val name: String,
    val year: String?,
    val artworkPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val rating: Double?,
    val availability: TitleAvailability,
    val mediaId: Int?,
    /** The TMDB genre ids the list gave, in its order; empty where it gave none. */
    val genreIds: List<Int> = emptyList(),
)

/** One page of a list. Pages count from 1. */
data class TitlePage(
    val results: List<TitleResult>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}

/** A genre with a backdrop to show, for the genre row. */
data class GenreTile(
    val id: Int,
    val name: String,
    val backdropPath: String?,
)

/** Where a list of titles comes from. Each source is one call, and [supportedBy] is whether the server has it. */
sealed interface TitleSource {
    /** The kind of every row of a list of one kind, for a row that arrives without a media type; the servers do set it. */
    val defaultKind: TitleKind?

    fun supportedBy(profile: SeerrServerProfile): Boolean = true

    data object Trending : TitleSource {
        override val defaultKind: TitleKind? = null
    }

    data class Popular(
        val kind: TitleKind,
    ) : TitleSource {
        override val defaultKind: TitleKind = kind
    }

    data class Upcoming(
        val kind: TitleKind,
    ) : TitleSource {
        override val defaultKind: TitleKind = kind

        override fun supportedBy(profile: SeerrServerProfile) = kind == TitleKind.Movie || profile.hasUpcomingTv
    }

    data class ByGenre(
        val kind: TitleKind,
        val genreId: Int,
    ) : TitleSource {
        override val defaultKind: TitleKind = kind

        override fun supportedBy(profile: SeerrServerProfile) = profile.hasDiscoverByCategory
    }

    /** A movie studio, or for a series a TV network. */
    data class ByCompany(
        val kind: TitleKind,
        val companyId: Int,
    ) : TitleSource {
        override val defaultKind: TitleKind = kind

        override fun supportedBy(profile: SeerrServerProfile) = profile.hasDiscoverByCategory
    }

    data class ByLanguage(
        val kind: TitleKind,
        val language: String,
    ) : TitleSource {
        override val defaultKind: TitleKind = kind

        override fun supportedBy(profile: SeerrServerProfile) = profile.hasDiscoverByCategory
    }

    data class ByKeyword(
        val kind: TitleKind,
        val keywordId: Int,
    ) : TitleSource {
        override val defaultKind: TitleKind = kind

        override fun supportedBy(profile: SeerrServerProfile) = profile.hasKeywordDiscover
    }

    /** Titles carrying any of the keywords, through the base discover call, as the web client queries a custom keyword row. */
    data class ByKeywords(
        val kind: TitleKind,
        val keywordIds: List<Int>,
    ) : TitleSource {
        override val defaultKind: TitleKind = kind

        override fun supportedBy(profile: SeerrServerProfile) = profile.hasKeywordDiscover
    }

    /** Titles on some streaming services in a region. */
    data class ByStreaming(
        val kind: TitleKind,
        val region: String,
        val providerIds: List<Int>,
    ) : TitleSource {
        override val defaultKind: TitleKind = kind

        override fun supportedBy(profile: SeerrServerProfile) = profile.hasWatchProviderDiscover
    }

    data object Watchlist : TitleSource {
        override val defaultKind: TitleKind? = null

        override fun supportedBy(profile: SeerrServerProfile) = profile.hasDiscoverWatchlist
    }

    data class Search(
        val query: String,
    ) : TitleSource {
        override val defaultKind: TitleKind? = null
    }
}
