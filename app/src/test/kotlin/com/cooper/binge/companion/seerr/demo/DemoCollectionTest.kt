package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/** The first demo film is in a collection of three, so the film page's rail has films to show; any other film has no collection. */
class DemoCollectionTest {
    private val apis = SeerrApiFactory(logRequests = false)

    private fun demo(block: suspend (SeerrApi) -> Unit) = runTest { apis.anonymous(DEMO_ORIGIN, block) }

    @Test
    fun `a film in the demo collection names it and the collection lists its films`() =
        demo { api ->
            val reference = requireNotNull(api.movieFullDetails(FIRST_DEMO_FILM).collection)

            val collection = api.collection(reference.id)

            assertEquals(reference.name, collection.name)
            assertEquals(3, collection.parts.size)
            assertTrue(collection.parts.any { it.id == FIRST_DEMO_FILM })
            assertTrue(collection.parts.all { it.mediaType == "movie" && it.mediaInfo != null })
        }

    @Test
    fun `a film outside the collection has none and an unknown collection is not found`() =
        demo { api ->
            assertEquals(null, api.movieFullDetails(MADE_UP_FILM).collection)
            val failure = runCatchingHttp { api.collection(MADE_UP_FILM) }
            assertEquals(404, failure?.code())
        }

    private suspend fun runCatchingHttp(block: suspend () -> Unit): HttpException? =
        try {
            block()
            null
        } catch (failure: HttpException) {
            failure
        }
}

private const val FIRST_DEMO_FILM = 10378
private const val MADE_UP_FILM = 777_002
