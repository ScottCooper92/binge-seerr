package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.seerr.isOpenableSite

/** The sites a title or a person links out to. */
enum class ExternalLinkKind { Imdb, Tmdb, Website }

/** One page about a title or a person on another site, opened outside the app. */
data class ExternalLink(
    val kind: ExternalLinkKind,
    val url: String,
)

/** What an IMDb page is for: a title (`tt…`) or a person (`nm…`), which sets the id's shape and the path. */
enum class ImdbPage(
    private val prefix: String,
    private val path: String,
) {
    Title("tt", "title"),
    Name("nm", "name"),
    ;

    private val idShape = Regex("$prefix\\d+")

    internal fun urlOf(id: String): String? = id.takeIf { idShape.matches(it) }?.let { "https://www.imdb.com/$path/$it" }
}

/**
 * The pages the server named for a title or a person, in the order IMDb, TMDB, website. [tmdbUrl] is the TMDB page where the app knows
 * one from its own id, and null where it shows none. An id that is not the shape of an [imdbPage] id, or an address that is not a
 * site, is dropped.
 */
internal fun externalLinks(
    imdbPage: ImdbPage,
    imdbId: String?,
    tmdbUrl: String?,
    homepage: String?,
): List<ExternalLink> =
    listOfNotNull(
        imdbId?.let(imdbPage::urlOf)?.let { ExternalLink(ExternalLinkKind.Imdb, it) },
        tmdbUrl?.let { ExternalLink(ExternalLinkKind.Tmdb, it) },
        homepage?.takeIf { isOpenableSite(it) }?.let { ExternalLink(ExternalLinkKind.Website, it) },
    )
