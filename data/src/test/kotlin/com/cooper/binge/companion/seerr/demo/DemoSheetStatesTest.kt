package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.PERMISSION_ADMIN
import com.cooper.binge.companion.seerr.seerr.PERMISSION_REQUEST
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrIssueStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/** The thirteen states the request sheet is built against, each answering as the plan's table says, and the role switch. */
class DemoSheetStatesTest {
    private val apis = SeerrApiFactory(logRequests = false)

    private fun demo(block: suspend (SeerrApi) -> Unit) = runTest { apis.anonymous(DEMO_ORIGIN, block) }

    private suspend fun SeerrApi.movie(tmdbId: Int) = movieDetails(tmdbId)

    private suspend fun SeerrApi.series(tmdbId: Int) = tvDetails(tmdbId)

    private suspend fun SeerrApi.movieInfo(tmdbId: Int) = movieDetails(tmdbId).mediaInfo!!

    private suspend fun SeerrApi.seriesInfo(tmdbId: Int) = tvDetails(tmdbId).mediaInfo!!

    @Test
    fun `1 not requested has no request and no media row to open`() =
        demo { api ->
            val info = api.movie(ELEPHANTS_DREAM).mediaInfo

            assertEquals(SeerrMediaStatusCode.Unknown, info?.status ?: SeerrMediaStatusCode.Unknown)
            assertTrue(info?.requests.orEmpty().isEmpty())
        }

    @Test
    fun `2 a pending request that is someone else's`() =
        demo { api ->
            val request = api.movieInfo(SINTEL).requests.single()

            assertEquals(SeerrRequestStatusCode.Pending, request.status)
            assertEquals("Sam", request.requestedBy?.displayName)
        }

    @Test
    fun `3 a pending request that is the signed-in user's own`() =
        demo { api ->
            val request = api.movieInfo(NOSFERATU).requests.single()

            assertEquals(SeerrRequestStatusCode.Pending, request.status)
            assertEquals(DEMO_USER_ID, request.requestedBy?.id)
        }

    @Test
    fun `4 approved and not downloading has no queue entry`() =
        demo { api ->
            val info = api.movie(BIG_BUCK_BUNNY).mediaInfo!!

            assertEquals(SeerrMediaStatusCode.Processing, info.status)
            assertEquals(SeerrRequestStatusCode.Approved, info.requests.single().status)
            assertTrue(info.downloadStatus.isEmpty())
        }

    @Test
    fun `5 downloading carries the size left the time left and an ETA`() =
        demo { api ->
            val download = api.seriesInfo(EXPANSE).downloadStatus.single()

            assertEquals("downloading", download.status)
            assertNotNull(download.size)
            assertNotNull(download.sizeLeft)
            assertNotNull(download.timeLeft)
            assertNotNull(download.estimatedCompletionTime)
        }

    @Test
    fun `6 queued and paused are two queue entries`() =
        demo { api ->
            val info = api.movie(CAMINANDES).mediaInfo!!

            assertEquals(listOf("queued", "paused"), info.downloadStatus.map { it.status })
            assertEquals(SeerrMediaStatusCode.Processing, info.status)
        }

    @Test
    fun `7 a series is partly available with a season in each state and one queue entry`() =
        demo { api ->
            val info = api.series(SEVERANCE).mediaInfo!!

            assertEquals(SeerrMediaStatusCode.PartiallyAvailable, info.status)
            assertEquals(
                mapOf(
                    1 to SeerrMediaStatusCode.Available,
                    2 to SeerrMediaStatusCode.Pending,
                    3 to SeerrMediaStatusCode.Processing,
                ),
                info.seasons.associate { it.seasonNumber to it.status },
            )
            assertEquals(1, info.downloadStatus.size)
        }

    @Test
    fun `8 an available title carries its media server link id and added date and answers watch data`() =
        demo { api ->
            val info = api.movie(METROPOLIS).mediaInfo!!

            assertEquals(SeerrMediaStatusCode.Available, info.status)
            assertNotNull(info.jellyfinMediaId)
            assertTrue(checkNotNull(info.mediaUrl).contains(checkNotNull(info.jellyfinMediaId)))
            assertNotNull(info.mediaAddedAt)
            val watch = api.watchData(checkNotNull(info.id))
            assertTrue(checkNotNull(watch.data).playCount > 0)
            assertNull(watch.data4k)
        }

    @Test
    fun `9 available with a pending 4K request`() =
        demo { api ->
            val info = api.movie(SPIRITED_AWAY).mediaInfo!!

            assertEquals(SeerrMediaStatusCode.Available, info.status)
            assertEquals(SeerrMediaStatusCode.Pending, info.status4k)
            assertEquals(listOf(false, true), info.requests.map { it.is4k }.sorted())
            val fourK = info.requests.single { it.is4k }
            assertEquals("Sam", fourK.requestedBy?.displayName)
            assertEquals(SeerrRequestStatusCode.Pending, fourK.status)
            assertNotNull(api.watchData(checkNotNull(info.id)).data)
        }

    @Test
    fun `10 several requests from different users and a 4K one by the signed-in user`() =
        demo { api ->
            val info = api.series(DUNE).mediaInfo!!

            assertEquals(3, info.requests.size)
            val first = info.requests.single { it.seasons.map { s -> s.seasonNumber } == listOf(1) && !it.is4k }
            val second = info.requests.single { it.seasons.map { s -> s.seasonNumber } == listOf(2) && !it.is4k }
            val fourK = info.requests.single { it.is4k }
            assertEquals("Sam" to SeerrRequestStatusCode.Pending, first.requestedBy?.displayName to first.status)
            assertEquals("Jordan" to SeerrRequestStatusCode.Approved, second.requestedBy?.displayName to second.status)
            assertEquals(DEMO_USER_ID, fourK.requestedBy?.id)
        }

    @Test
    fun `11 declined and 12 failed are the requests' own states`() =
        demo { api ->
            val declined = api.movieInfo(TEARS_OF_STEEL).requests.single()
            val failed = api.movieInfo(THE_MATRIX).requests.single()

            assertEquals(SeerrRequestStatusCode.Declined, declined.status)
            assertEquals(SeerrRequestStatusCode.Failed, failed.status)
        }

    @Test
    fun `13 the issue thread is on the title that opens with an issue`() =
        demo { api ->
            val onIt = api.issues(take = 20).results.filter { it.media?.tmdbId == NIGHT_OF_THE_LIVING_DEAD }

            assertEquals(2, onIt.size)
            assertTrue(onIt.all { it.status == SeerrIssueStatusCode.Open })
            assertTrue(onIt.any { it.comments.size == 2 })
            assertTrue(api.issues(take = 20).results.none { it.media?.tmdbId == METROPOLIS })
        }

    @Test
    fun `a 4K request is sent to the 4K server and both servers answer a profile`() =
        demo { api ->
            val fourK = api.requests(take = 50).results.first { it.is4k && it.media.tmdbId == SPIRITED_AWAY }
            val servers = api.radarrServers()

            assertEquals(DEMO_4K_SERVER_ID, fourK.serverId)
            assertEquals(listOf(false, true), servers.map { it.is4k }.sorted())
            assertTrue(servers.all { server -> api.radarrServer(server.id).profiles.isNotEmpty() })
            assertTrue(api.sonarrServers().all { server -> api.sonarrServer(server.id).profiles.isNotEmpty() })
        }

    @Test
    fun `a title with no watch data answers not found as before`() =
        demo { api ->
            val sintel = checkNotNull(api.movie(SINTEL).mediaInfo?.id)

            val code =
                try {
                    api.watchData(sintel)
                    0
                } catch (e: HttpException) {
                    e.code()
                }
            assertEquals(404, code)
        }

    @Test
    fun `viewing as a requester answers the request permission only and the administrator again after`() =
        demo { api ->
            assertEquals(PERMISSION_ADMIN, api.authenticatedUser().permissions)
            assertFalse(apis.demoViewer)

            apis.viewDemoAs(true)
            assertEquals(PERMISSION_REQUEST, api.authenticatedUser().permissions)
            assertTrue(apis.demoViewer)

            apis.viewDemoAs(false)
            assertEquals(PERMISSION_ADMIN, api.authenticatedUser().permissions)
        }

    @Test
    fun `resetting the demo goes back to the administrator`() =
        demo { api ->
            apis.viewDemoAs(true)

            apis.resetDemo()

            assertEquals(PERMISSION_ADMIN, api.authenticatedUser().permissions)
            assertFalse(apis.demoViewer)
        }

    private companion object {
        const val ELEPHANTS_DREAM = 900_001
        const val CAMINANDES = 900_002
        const val DUNE = 900_003
        const val SINTEL = 45745
        const val NOSFERATU = 653
        const val BIG_BUCK_BUNNY = 10378
        const val EXPANSE = 63639
        const val SEVERANCE = 95396
        const val METROPOLIS = 19
        const val SPIRITED_AWAY = 129
        const val TEARS_OF_STEEL = 133701
        const val THE_MATRIX = 603
        const val NIGHT_OF_THE_LIVING_DEAD = 10331
    }
}
