package com.cooper.binge.companion.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Test

/** The company search answers with TMDB's own snake_case keys, where a slider's studio is camelCase. */
class SeerrCompanySearchDecodingTest {
    private val json = SeerrApiFactory(logRequests = false).json

    @Test
    fun `a company search page reads the snake_case logo and page counts`() {
        val page =
            json.decodeFromString<SeerrCompanyPageDto>(
                """{"page":1,"results":[{"id":2,"logo_path":"/disney.png","name":"Disney","origin_country":"US"}],""" +
                    """"total_pages":3,"total_results":55}""",
            )

        assertEquals("/disney.png", page.results.single().logoPath)
        assertEquals(3, page.totalPages)
        assertEquals(55, page.totalResults)
    }

    @Test
    fun `a slider's studio still reads its camelCase logo`() {
        val company = json.decodeFromString<SeerrCompanyDto>("""{"id":2,"name":"Disney","logoPath":"/disney.png"}""")

        assertEquals("/disney.png", company.logoPath)
    }
}
