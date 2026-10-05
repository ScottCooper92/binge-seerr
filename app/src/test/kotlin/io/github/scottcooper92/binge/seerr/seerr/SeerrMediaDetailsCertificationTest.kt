package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The age rating off a title lookup, decoded with the `Json` the API client uses. A movie's rating is in its release
 * dates and a show's in its content ratings; both are by country, and the title's own country is not the viewer's.
 */
class SeerrMediaDetailsCertificationTest {
    private val json = SeerrApiFactory(logRequests = false).json

    private fun decode(body: String) = json.decodeFromString(SeerrMediaDetailsDto.serializer(), body)

    private val movie =
        decode(
            """
            {"title":"Heat","releases":{"results":[
              {"iso_3166_1":"GB","release_dates":[{"certification":"","type":3},{"certification":"15","type":3}]},
              {"iso_3166_1":"US","release_dates":[{"certification":"R","type":3}]}]}}
            """.trimIndent(),
        )

    private val show =
        decode(
            """
            {"name":"Severance","contentRatings":{"results":[
              {"iso_3166_1":"US","rating":"TV-MA"},{"iso_3166_1":"GB","rating":"18"}]}}
            """.trimIndent(),
        )

    @Test
    fun `a movie reads the rating for the viewer's country`() {
        assertEquals("15", movie.certification("GB"))
    }

    @Test
    fun `a blank release date is passed over for the next one in the same country`() {
        assertEquals("15", movie.certification("GB"))
    }

    @Test
    fun `a show reads the rating for the viewer's country`() {
        assertEquals("18", show.certification("GB"))
    }

    @Test
    fun `a country with no rating falls back to the US one`() {
        assertEquals("R", movie.certification("FR"))
        assertEquals("TV-MA", show.certification("FR"))
    }

    @Test
    fun `a title with no ratings at all has none`() {
        assertNull(decode("""{"title":"Unreleased"}""").certification("US"))
    }
}
