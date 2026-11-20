package com.cooper.binge.companion.seerr.discover.request

import androidx.paging.PagingSource
import com.cooper.binge.companion.seerr.data.CacheDatabaseRule
import com.cooper.binge.companion.seerr.data.RequestEntity
import com.cooper.binge.companion.seerr.data.RoomRequestStore
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.PERMISSION_ADMIN
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val CREATED =
    """{"id":31,"status":1,"is4k":false,"createdAt":"2026-11-20T10:00:00.000Z",
       "requestedBy":{"id":9,"displayName":"Alex"},
       "media":{"id":5,"tmdbId":603,"mediaType":"movie","status":3},"seasons":[]}"""

/** Requesting a title from its page puts the request in the Requests list at once, with no refresh and no poll. */
@RunWith(RobolectricTestRunner::class)
class NewRequestInListTest {
    @get:Rule
    val database = CacheDatabaseRule()

    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun existing(listKey: String) =
        RequestEntity(
            listKey = listKey,
            id = 1,
            tmdbId = 1,
            mediaType = "movie",
            title = "Older",
            posterUrl = null,
            year = null,
            requestedBy = null,
            requestedById = 9,
            requestedAtMillis = null,
            status = 2,
            mediaStatus = null,
            downloadFraction = null,
            downloadEtaMinutes = null,
            downloading = false,
            seasonNumbers = "",
            is4k = false,
            orderIndex = 0,
        )

    private fun requestFrom(answer: FakeResponse): List<String> =
        runBlocking {
            val store = RoomRequestStore(database.db)
            store.refresh("all:added:all", listOf(existing("all:added:all")), nextSkip = null)
            seerr.dispatcher = { answer }
            val result =
                factory.anonymous(seerr.url()) { api ->
                    val profile =
                        SeerrServerProfile(SeerrVariant.Seerr, SeerrVersion(3, 2, 0), settings = SeerrPublicSettings())
                    SeerrRequestSheetRepository(
                        api = { api },
                        viewer = { SeerrUserDto(id = 9, permissions = PERMISSION_ADMIN) },
                        profile = { profile },
                        io = Dispatchers.Unconfined,
                        requests = store,
                        titles = { _, _, _ -> HydratedTitle(title = "The Matrix", posterUrl = null, year = "1999") },
                    ).submit(RequestSubmission(TitleKind.Movie, 603, emptyList(), is4k = false, destination = null))
                }
            assertEquals(RequestResult.Created, result)
            (
                store.pagingSource("all:added:all").load(
                    PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
                ) as PagingSource.LoadResult.Page
            ).data.map { "${it.id}:${it.title}" }
        }

    @Test
    fun `a request made from a title page is the first row of the list without a refresh`() {
        val rows = requestFrom(FakeResponse(headers = headersOf("Content-Type", "application/json"), body = CREATED))

        assertEquals(listOf("31:The Matrix", "1:Older"), rows)
    }

    @Test
    fun `an answer with no request in it leaves the list as it was`() {
        val rows = requestFrom(FakeResponse(headers = headersOf("Content-Type", "application/json"), body = """{"id":31}"""))

        assertEquals(listOf("1:Older"), rows)
    }

    @Test
    fun `an answer the server shaped oddly is still a made request and leaves the list as it was`() {
        val odd = """{"id":31,"media":{"id":5},"seasons":null,"requestedBy":"nobody"}"""

        val rows = requestFrom(FakeResponse(headers = headersOf("Content-Type", "application/json"), body = odd))

        assertEquals(listOf("1:Older"), rows)
    }
}
