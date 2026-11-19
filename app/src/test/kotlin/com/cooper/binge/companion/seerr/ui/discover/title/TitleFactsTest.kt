package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.component.ExternalScoreKind
import com.binge.designsystem.component.InfoValue
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.discover.title.VersionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TitleFactsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun movie(
        crew: List<TitlePerson>,
        releaseDate: String? = "1999-03-31",
    ) = MovieDetails(
        tmdbId = 1,
        title = "A film",
        tagline = null,
        overview = null,
        posterUrl = null,
        backdropUrl = null,
        year = "1999",
        runtimeMinutes = null,
        certification = null,
        voteAverage = null,
        voteCount = null,
        genres = emptyList(),
        keywords = emptyList(),
        status = "Released",
        originalLanguage = "en",
        spokenLanguageCodes = emptyList(),
        studios = listOf(TitleCompany(1, "One Studio"), TitleCompany(2, "Another")),
        budget = null,
        revenue = null,
        cast = emptyList(),
        keyCrew = crew,
        otherCrew = emptyList(),
        videos = emptyList(),
        providers = null,
        collection = null,
        standard = VersionState(TitleAvailability.None, mayRequest = true),
        fourK = null,
        mediaId = null,
        webUrl = "https://seerr.example/movie/1",
        releaseDate = releaseDate,
    )

    private fun person(
        name: String,
        role: String,
    ) = TitlePerson(name.length, name, role, null)

    private fun List<com.binge.designsystem.component.DetailInfoEntry>.valueOf(label: String) =
        (first { it.label == label }.value as com.binge.designsystem.component.InfoValue.Plain).text

    @Test
    fun `the director is whoever the server bills for directing`() {
        val facts =
            movie(listOf(person("Ann Writer", "Writer"), person("Bo Director", "Director, Writer"))).detailEntries(resources, Locale.US)
        assertEquals("Bo Director", facts.valueOf("Directed by"))
    }

    @Test
    fun `each director is a link to their page where the page can open one`() {
        val opened = mutableListOf<Int>()
        val crew = listOf(person("Ann Writer", "Writer"), person("Bo Director", "Director, Writer"), person("Cy Director", "Director"))
        val facts = movie(crew).detailEntries(resources, Locale.US, onOpenPerson = { opened += it })
        val links = (facts.first { it.label == "Directed by" }.value as InfoValue.Links).links
        assertEquals(listOf("Bo Director", "Cy Director"), links.map { it.text })
        links.first().onClick()
        assertEquals(listOf("Bo Director".length), opened)
    }

    @Test
    fun `directors stay plain text where the page cannot open a person`() {
        val facts = movie(listOf(person("Bo Director", "Director"))).detailEntries(resources, Locale.US)
        assertEquals("Bo Director", facts.valueOf("Directed by"))
    }

    @Test
    fun `each creator is a link to their page where the page can open one`() {
        val opened = mutableListOf<Int>()
        val series =
            seriesFixture().copy(
                creators = listOf(TitlePerson(7, "Ann Creator", null, null), TitlePerson(8, "Bo Creator", null, null)),
            )
        val facts = series.detailEntries(resources, Locale.US, onOpenPerson = { opened += it })
        val links = (facts.first { it.label == "Created by" }.value as InfoValue.Links).links
        assertEquals(listOf("Ann Creator", "Bo Creator"), links.map { it.text })
        links.last().onClick()
        assertEquals(listOf(8), opened)
    }

    @Test
    fun `creators stay plain text where the page cannot open a person`() {
        val series =
            seriesFixture().copy(
                creators = listOf(TitlePerson(7, "Ann Creator", null, null), TitlePerson(8, "Bo Creator", null, null)),
            )
        assertEquals("Ann Creator, Bo Creator", series.detailEntries(resources, Locale.US).valueOf("Created by"))
    }

    @Test
    fun `studios are links that name their company where the page can open one`() {
        val opened = mutableListOf<TitleCompany>()
        val facts = movie(emptyList()).detailEntries(resources, Locale.US, onOpenCompany = { opened += it })
        val links = (facts.first { it.label == "Studios" }.value as InfoValue.Links).links
        assertEquals(listOf("One Studio", "Another"), links.map { it.text })
        links.last().onClick()
        assertEquals(listOf(TitleCompany(2, "Another")), opened)
    }

    @Test
    fun `studios stay plain text where the page cannot open a company`() {
        val facts = movie(emptyList()).detailEntries(resources, Locale.US)
        assertEquals(InfoValue.Plain("One Studio, Another"), facts.first { it.label == "Studios" }.value)
    }

    @Test
    fun `a film with no studios has no studio links`() {
        val facts = movie(emptyList()).copy(studios = emptyList()).detailEntries(resources, Locale.US, onOpenCompany = { })
        assertEquals("", facts.valueOf("Studios"))
    }

    @Test
    fun `the release date is written the way the reader's language writes it`() {
        val facts = movie(emptyList()).detailEntries(resources, Locale.US)
        assertEquals("Mar 31, 1999", facts.valueOf("Release date"))
    }

    @Test
    fun `a fact the server did not give has an empty value, which the list does not draw`() {
        val facts = movie(emptyList(), releaseDate = null).detailEntries(resources, Locale.US)
        assertEquals("", facts.valueOf("Directed by"))
        assertEquals("", facts.valueOf("Release date"))
    }

    @Test
    fun `the server's other ratings are the header's scores, critics then audience then IMDb`() {
        val scores = TitleRatings(83, 85, 8.7).toExternalScores(resources, Locale.US)
        assertEquals(listOf(ExternalScoreKind.CriticsFresh, ExternalScoreKind.Audience, ExternalScoreKind.Imdb), scores.map { it.kind })
        assertEquals(listOf("83%", "85%", "8.7"), scores.map { it.value })
        assertEquals(
            listOf("Critics 83 percent", "Audience 85 percent", "IMDb 8.7 out of 10"),
            scores.map { it.contentDescription },
        )
    }

    @Test
    fun `a critics score under 60 percent is a rotten one and 60 is still fresh`() {
        assertEquals(ExternalScoreKind.CriticsRotten, TitleRatings(59, null, null).toExternalScores(resources, Locale.US).single().kind)
        assertEquals(ExternalScoreKind.CriticsFresh, TitleRatings(60, null, null).toExternalScores(resources, Locale.US).single().kind)
    }

    @Test
    fun `a score the server did not give is left out, and no ratings give no scores`() {
        assertEquals(listOf("85%"), TitleRatings(null, 85, null).toExternalScores(resources, Locale.US).map { it.value })
        assertEquals(emptyList<String>(), (null as TitleRatings?).toExternalScores(resources, Locale.US).map { it.value })
    }

    @Test
    fun `the facts hold neither the scores nor the collection, which the header and its own rail carry`() {
        val labels = movie(emptyList()).detailEntries(resources, Locale.US).map { it.label }
        assertFalse(labels.any { it == "Critics" || it == "Audience" || it == "IMDb" || it == "Collection" })
    }

    @Test
    fun `one rail of similar titles holds each title once`() {
        fun result(id: Int) = TitleResult(TitleKind.Movie, id, "T$id", null, null, null, null, null, TitleAvailability.None, null)
        val extras = TitleExtras(null, listOf(result(1), result(2)), listOf(result(2), result(3)))
        assertEquals(listOf(1, 2, 3), extras.moreLikeThis.map { it.tmdbId })
    }
}
