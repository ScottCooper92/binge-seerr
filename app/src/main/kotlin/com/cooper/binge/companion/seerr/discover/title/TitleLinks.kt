package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.seerr.isOpenableSite

/** The sites a title page links out to. */
enum class TitleLinkKind { Imdb, Tmdb, Website }

/** One page about the title on another site, opened outside the app. */
data class TitleLink(
    val kind: TitleLinkKind,
    val url: String,
)

private val IMDB_TITLE_ID = Regex("tt\\d+")

/**
 * The pages the server named for a title, and its TMDB page, which the server's own TMDB id gives. [tmdbPath] is `movie` or `tv`.
 * An id that is not an IMDb title id, or an address that is not a site, is dropped.
 */
internal fun titleLinks(
    tmdbPath: String,
    tmdbId: Int,
    imdbId: String?,
    homepage: String?,
): List<TitleLink> =
    listOfNotNull(
        imdbId?.takeIf { IMDB_TITLE_ID.matches(it) }?.let { TitleLink(TitleLinkKind.Imdb, "https://www.imdb.com/title/$it") },
        TitleLink(TitleLinkKind.Tmdb, "https://www.themoviedb.org/$tmdbPath/$tmdbId"),
        homepage?.takeIf { isOpenableSite(it) }?.let { TitleLink(TitleLinkKind.Website, it) },
    )
