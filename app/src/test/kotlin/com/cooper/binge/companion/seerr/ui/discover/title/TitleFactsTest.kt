package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
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
        studios = listOf("One Studio", "Another"),
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

    private fun List<com.binge.designsystem.component.InfoRowEntry>.valueOf(label: String) =
        (first { it.label == label }.value as com.binge.designsystem.component.InfoValue.Plain).text

    @Test
    fun `the director is whoever the server bills for directing`() {
        val facts =
            movie(listOf(person("Ann Writer", "Writer"), person("Bo Director", "Director, Writer"))).factEntries(resources, Locale.US, null)
        assertEquals("Bo Director", facts.valueOf("Directed by"))
    }

    @Test
    fun `the release date is written the way the reader's language writes it`() {
        val facts = movie(emptyList()).factEntries(resources, Locale.US, null)
        assertEquals("Mar 31, 1999", facts.valueOf("Release date"))
    }

    @Test
    fun `a fact the server did not give has an empty value, which the list does not draw`() {
        val facts = movie(emptyList(), releaseDate = null).factEntries(resources, Locale.US, null)
        assertEquals("", facts.valueOf("Directed by"))
        assertEquals("", facts.valueOf("Release date"))
    }

    @Test
    fun `the server's other ratings follow as facts`() {
        val facts = movie(emptyList()).factEntries(resources, Locale.US, TitleRatings(83, 85, 8.7))
        assertEquals("83%", facts.valueOf("Critics"))
        assertEquals("85%", facts.valueOf("Audience"))
        assertEquals("8.7", facts.valueOf("IMDb"))
        assertFalse(movie(emptyList()).factEntries(resources, Locale.US, null).valueOf("Critics").isNotEmpty())
    }

    @Test
    fun `one rail of similar titles holds each title once`() {
        fun result(id: Int) = TitleResult(TitleKind.Movie, id, "T$id", null, null, null, null, null, TitleAvailability.None, null)
        val extras = TitleExtras(null, listOf(result(1), result(2)), listOf(result(2), result(3)))
        assertEquals(listOf(1, 2, 3), extras.moreLikeThis.map { it.tmdbId })
    }
}
