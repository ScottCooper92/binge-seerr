package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PERSON_ID = 9001

/** The person page's reads answer from the demo's model: a person the title pages list, and credits that are demo titles. */
class DemoPersonTest {
    private val apis = SeerrApiFactory(logRequests = false)

    private fun demo(block: suspend (SeerrApi) -> Unit) = runTest { apis.anonymous(DEMO_ORIGIN, block) }

    @Test
    fun `a person the title pages list has a page and credits that are demo titles, one of them listed twice`() =
        demo { api ->
            val person = api.personDetails(PERSON_ID)
            val credits = api.personCredits(PERSON_ID)
            val castIds = credits.cast.map { it.id }

            assertEquals("Alex Sample", person.name)
            assertTrue(castIds.isNotEmpty())
            assertTrue(castIds.size > castIds.distinct().size)
            assertTrue(credits.cast.all { credit -> DEMO_TITLES.any { it.tmdbId == credit.id } })
            assertTrue(credits.crew.all { it.job == "Director" })
        }
}
