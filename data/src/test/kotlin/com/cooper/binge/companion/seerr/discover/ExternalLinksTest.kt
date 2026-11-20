package com.cooper.binge.companion.seerr.discover

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalLinksTest {
    private val tmdb = "https://www.themoviedb.org/movie/603"

    @Test
    fun `every link the server gave is kept in the order IMDb TMDB website`() {
        assertEquals(
            listOf(
                ExternalLink(ExternalLinkKind.Imdb, "https://www.imdb.com/title/tt0133093"),
                ExternalLink(ExternalLinkKind.Tmdb, tmdb),
                ExternalLink(ExternalLinkKind.Website, "https://show.example.org/"),
            ),
            externalLinks(ImdbPage.Title, "tt0133093", tmdb, "https://show.example.org/"),
        )
    }

    @Test
    fun `a person's IMDb page is a name page and a person has no TMDB chip`() {
        assertEquals(
            listOf(
                ExternalLink(ExternalLinkKind.Imdb, "https://www.imdb.com/name/nm0000206"),
                ExternalLink(ExternalLinkKind.Website, "https://person.example.org/"),
            ),
            externalLinks(ImdbPage.Name, "nm0000206", null, "https://person.example.org/"),
        )
    }

    @Test
    fun `an id of the wrong shape for the page is dropped`() {
        assertEquals(listOf(ExternalLink(ExternalLinkKind.Tmdb, tmdb)), externalLinks(ImdbPage.Title, "nm0000206", tmdb, null))
        assertEquals(emptyList<ExternalLink>(), externalLinks(ImdbPage.Name, "tt0133093", null, null))
    }

    @Test
    fun `a website that cannot be a real site is dropped`() {
        assertEquals(
            listOf(ExternalLink(ExternalLinkKind.Tmdb, tmdb)),
            externalLinks(ImdbPage.Title, null, tmdb, "https://demo.seerr.invalid/films/1"),
        )
        assertEquals(1, externalLinks(ImdbPage.Title, " ", tmdb, "javascript:alert(1)").size)
    }
}
