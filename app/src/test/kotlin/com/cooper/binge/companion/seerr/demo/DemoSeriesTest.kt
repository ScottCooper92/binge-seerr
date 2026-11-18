package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.seasonAvailability
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrRequestBody
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A show the catalog does not list is made up on first use, with no request seeded for it. */
private const val MADE_UP_SHOW_ID = 777_001

/** The series page's reads answer from the demo's model, and a request for a season changes the next read of that season. */
class DemoSeriesTest {
    private val apis = SeerrApiFactory(logRequests = false)
    private val showId = MADE_UP_SHOW_ID

    private fun demo(block: suspend (SeerrApi) -> Unit) = runTest { apis.anonymous(DEMO_ORIGIN, block) }

    @Test
    fun `the whole show reads with its seasons, facts and people`() =
        demo { api ->
            val dto = api.tvFullDetails(showId)

            assertTrue(dto.name.isNotBlank())
            assertTrue(dto.seasons.size >= 2)
            assertTrue(dto.networks.isNotEmpty())
            assertTrue(
                dto.credits
                    ?.cast
                    .orEmpty()
                    .isNotEmpty(),
            )
            assertEquals("Returning Series", dto.status)
        }

    @Test
    fun `a request for one season covers that season and leaves the rest open`() =
        demo { api ->
            val before = api.tvFullDetails(showId).mediaInfo
            assertEquals(TitleAvailability.None, before.seasonAvailability(1, is4k = false))

            api.requestMedia(SeerrRequestBody(mediaType = "tv", mediaId = showId, seasons = listOf(1)))

            val after = api.tvFullDetails(showId).mediaInfo
            assertFalse(after.seasonAvailability(1, is4k = false) == TitleAvailability.None)
            assertEquals(TitleAvailability.None, after.seasonAvailability(2, is4k = false))
        }

    @Test
    fun `the score, recommendations and similar shows answer, and are other shows`() =
        demo { api ->
            assertTrue(api.tvRatings(showId).criticsScore != null)
            val recommendations = api.tvRecommendations(showId).results
            assertTrue(recommendations.none { it.id == showId })
            assertTrue(api.tvSimilar(showId).results.none { it.id == showId })
        }

    @Test
    fun `a person the title pages list has a page and credits that are demo titles, one of them listed twice`() =
        demo { api ->
            val person = api.personDetails(PERSON_ID)
            val credits = api.personCredits(PERSON_ID)

            assertEquals("Alex Sample", person.name)
            assertTrue(credits.cast.isNotEmpty())
            assertTrue(
                credits.cast.map { it.id }.size >
                    credits.cast
                        .map { it.id }
                        .distinct()
                        .size,
            )
            assertTrue(credits.cast.all { credit -> DEMO_TITLES.any { it.tmdbId == credit.id } })
            assertTrue(credits.crew.all { it.job == "Director" })
        }
}

private const val PERSON_ID = 9001
