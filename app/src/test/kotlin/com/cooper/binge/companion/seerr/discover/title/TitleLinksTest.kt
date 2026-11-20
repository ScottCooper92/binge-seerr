package com.cooper.binge.companion.seerr.discover.title

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleLinksTest {
    @Test
    fun `every link the server gave is kept in the order IMDb TMDB website`() {
        assertEquals(
            listOf(
                TitleLink(TitleLinkKind.Imdb, "https://www.imdb.com/title/tt0133093"),
                TitleLink(TitleLinkKind.Tmdb, "https://www.themoviedb.org/tv/1399"),
                TitleLink(TitleLinkKind.Website, "https://show.example.org/"),
            ),
            titleLinks("tv", 1399, "tt0133093", "https://show.example.org/"),
        )
    }

    @Test
    fun `an id that is not an IMDb title id is dropped`() {
        assertEquals(
            listOf(TitleLink(TitleLinkKind.Tmdb, "https://www.themoviedb.org/movie/603")),
            titleLinks("movie", 603, "nm0000206", null),
        )
    }

    @Test
    fun `a website that cannot be a real site is dropped`() {
        assertEquals(
            listOf(TitleLink(TitleLinkKind.Tmdb, "https://www.themoviedb.org/movie/603")),
            titleLinks("movie", 603, null, "https://demo.seerr.invalid/films/1"),
        )
        assertEquals(1, titleLinks("movie", 603, " ", "javascript:alert(1)").size)
    }
}
