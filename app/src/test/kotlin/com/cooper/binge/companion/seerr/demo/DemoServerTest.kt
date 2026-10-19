package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrAddToBlocklistBody
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrCreateIssueBody
import com.cooper.binge.companion.seerr.seerr.SeerrIssueCommentBody
import com.cooper.binge.companion.seerr.seerr.SeerrIssueStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrIssueTypeCode
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestBody
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException

/** The demo server through the real client, one area at a time: a write changes what a later read answers. */
class DemoServerTest {
    private val apis = SeerrApiFactory(logRequests = false)

    private fun demo(block: suspend (SeerrApi) -> Unit) = runTest { apis.anonymous(DEMO_ORIGIN, block) }

    private suspend fun code(block: suspend () -> Unit): Int =
        try {
            block()
            fail("expected the call to fail")
            0
        } catch (e: HttpException) {
            e.code()
        }

    @Test
    fun `the seed has pending requests, two downloading, a failed one and a declined one`() =
        demo { api ->
            val count = api.requestCount()

            assertEquals(3, count.pending)
            assertEquals(2, count.processing)
            assertEquals(1, count.declined)
            assertEquals(2, api.requests(take = 20, filter = "processing").results.size)
            assertEquals(1, api.requests(take = 20, filter = "failed").results.size)
        }

    @Test
    fun `approving a request approves it, and it then reads as downloading`() =
        demo { api ->
            val sintel = api.requests(take = 20, filter = "pending").results.first { it.media.tmdbId == SINTEL }

            api.approveRequest(sintel.id)

            val after = api.request(sintel.id)
            assertEquals(SeerrRequestStatusCode.Approved, after.status)
            assertEquals(SeerrMediaStatusCode.Processing, after.media.status)
            assertEquals(2, api.requestCount().pending)
            assertEquals(3, api.requests(take = 20, filter = "processing").results.size)
        }

    @Test
    fun `declining and retrying move a request, and deleting removes it`() =
        demo { api ->
            val failed = api.requests(take = 20, filter = "failed").results.single()

            api.retryRequest(failed.id)
            assertEquals(SeerrRequestStatusCode.Approved, api.request(failed.id).status)
            api.declineRequest(failed.id)
            assertEquals(SeerrRequestStatusCode.Declined, api.request(failed.id).status)
            api.deleteRequest(failed.id)

            assertEquals(404, code { api.request(failed.id) })
        }

    @Test
    fun `a request pages and sorts, newest first`() =
        demo { api ->
            val first = api.requests(take = 3, skip = 0)
            val second = api.requests(take = 3, skip = 3)

            assertEquals(3, first.results.size)
            assertEquals(10, first.pageInfo.results)
            assertTrue(first.results.map { it.createdAt.orEmpty() }.let { dates -> dates == dates.sortedDescending() })
            assertTrue(
                first.results
                    .map { it.id }
                    .intersect(second.results.map { it.id }.toSet())
                    .isEmpty(),
            )
        }

    @Test
    fun `requesting a title the server holds is refused, a blocked one is refused, and a fresh one is made`() =
        demo { api ->
            assertEquals(409, api.requestMedia(SeerrRequestBody("movie", SINTEL)).code())
            assertEquals(403, api.requestMedia(SeerrRequestBody("movie", PARASITE)).code())

            val sintel = api.requests(take = 20, filter = "pending").results.first { it.media.tmdbId == SINTEL }
            api.deleteRequest(sintel.id)
            val made = api.requestMedia(SeerrRequestBody("movie", SINTEL)).body()?.id

            assertEquals(SeerrRequestStatusCode.Approved, api.request(made!!).status)
        }

    @Test
    fun `a title reads its status, and one the demo has no data for is made up and can be requested`() =
        demo { api ->
            assertEquals(SeerrMediaStatusCode.Available, api.movieDetails(METROPOLIS).mediaInfo?.status)
            assertEquals(
                1,
                api
                    .tvDetails(EXPANSE)
                    .mediaInfo
                    ?.requests
                    ?.size,
            )
            val unknown = api.movieDetails(MADE_UP)
            assertEquals("Inception", unknown.title)
            assertEquals("Requested title", api.movieDetails(NOT_A_TITLE).title)
            assertEquals(SeerrMediaStatusCode.Unknown, unknown.mediaInfo?.status)
            api.requestMedia(SeerrRequestBody("movie", MADE_UP))
            assertEquals(SeerrMediaStatusCode.Processing, api.movieDetails(MADE_UP).mediaInfo?.status)
        }

    @Test
    fun `an issue takes a comment, resolves, and one can be filed`() =
        demo { api ->
            val open = api.issues(take = 20, filter = "open").results
            assertEquals(2, open.size)
            val issue = open.first { it.comments.size > 1 }

            val commented = api.commentOnIssue(issue.id, SeerrIssueCommentBody("Fixed on my side."))
            api.setIssueStatus(issue.id, "resolved")

            assertEquals(issue.comments.size + 1, commented.comments.size)
            assertEquals(SeerrIssueStatusCode.Resolved, api.issue(issue.id).status)
            assertEquals(1, api.issueCount().open)
            api.createIssue(SeerrCreateIssueBody(mediaId = 6, issueType = SeerrIssueTypeCode.Video, message = "Blurry."))
            assertEquals(2, api.issueCount().open)
        }

    @Test
    fun `blocking a title lists it and ends its requests, and unblocking frees it`() =
        demo { api ->
            api.addToBlocklist("blocklist", SeerrAddToBlocklistBody(SINTEL, "movie", "Sintel", user = DEMO_USER_ID))

            assertEquals(2, api.blocklist("blocklist", take = 10).pageInfo.results)
            assertEquals(SeerrMediaStatusCode.Blocklisted, api.movieDetails(SINTEL).mediaInfo?.status)
            assertFalse(api.requests(take = 20).results.any { it.media.tmdbId == SINTEL })

            api.removeFromBlocklist("blocklist", SINTEL, mediaType = null)

            assertEquals(1, api.blocklist("blocklist", take = 10).pageInfo.results)
            assertEquals(SeerrMediaStatusCode.Unknown, api.movieDetails(SINTEL).mediaInfo?.status)
        }

    @Test
    fun `resetting the demo puts the seed back`() =
        demo { api ->
            api.requests(take = 20, filter = "pending").results.forEach { api.declineRequest(it.id) }
            assertEquals(0, api.requestCount().pending)

            apis.resetDemo()

            assertEquals(3, api.requestCount().pending)
        }

    private companion object {
        const val SINTEL = 45745
        const val PARASITE = 496243
        const val METROPOLIS = 19
        const val EXPANSE = 63639
        const val MADE_UP = 27205
        const val NOT_A_TITLE = 1
    }
}
