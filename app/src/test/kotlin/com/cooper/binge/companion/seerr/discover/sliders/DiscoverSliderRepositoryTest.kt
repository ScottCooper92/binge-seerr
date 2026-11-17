package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import com.cooper.binge.companion.seerr.ui.settings.server.SliderForm
import com.cooper.binge.companion.seerr.ui.settings.server.SliderType
import com.cooper.binge.companion.seerr.util.FakeRequest
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

private val JSON = headersOf("Content-Type", "application/json")

private fun sliderJson(
    id: Int,
    type: Int,
    enabled: Boolean = true,
    title: String? = null,
    data: String? = null,
    builtIn: Boolean = true,
) = """{"id":$id,"type":$type,"title":${title?.let {
    "\"$it\""
}},"isBuiltIn":$builtIn,"enabled":$enabled,"data":${data?.let { "\"$it\"" }}}"""

class DiscoverSliderRepositoryTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private val job = Job()
    private var profile = SeerrServerProfile(variant = SeerrVariant.Seerr, version = SeerrVersion(3, 2, 0))

    /** The server's list, as a GET answers it. */
    private var server = listOf(sliderJson(1, 4), sliderJson(2, 5), sliderJson(3, 9))
    private val answers = mutableMapOf<String, FakeResponse>()
    private val failing = mutableSetOf<String>()

    @After
    fun tearDown() {
        job.cancel()
        seerr.awaitIdle()
        seerr.close()
    }

    private fun serve(request: FakeRequest): FakeResponse {
        val key = "${request.method} ${request.url.encodedPath.removePrefix("/api/v1/settings/discover")}"
        if (key in failing) return FakeResponse(code = 500, headers = JSON, body = "{}")
        return answers[key]
            ?: if (key == "GET ") FakeResponse(headers = JSON, body = server.joinToString(",", "[", "]")) else FakeResponse(code = 404)
    }

    private fun <T> withRepository(
        changes: MutableSharedFlow<Any?> = MutableSharedFlow(replay = 1),
        block: suspend (SeerrDiscoverSliderRepository) -> T,
    ): T {
        seerr.dispatcher = ::serve
        return runBlocking {
            factory.anonymous(seerr.url()) { api ->
                block(
                    SeerrDiscoverSliderRepository(
                        { api },
                        { profile },
                        changes,
                        CoroutineScope(job + Dispatchers.Unconfined),
                        Dispatchers.Unconfined,
                    ),
                )
            }
        }
    }

    private fun ready(repository: DiscoverSliderRepository) = repository.sliders.value as SliderListState.Ready

    @Test
    fun `nothing is known until the list is read`() {
        withRepository { assertSame(SliderListState.Loading, it.sliders.value) }
    }

    @Test
    fun `a refresh publishes the server's list`() {
        withRepository { repository ->
            val list = repository.refresh()

            assertEquals(listOf(1, 2, 3), list.map { it.id })
            assertEquals(list, ready(repository).sliders)
            assertEquals(false, ready(repository).isDefault)
        }
    }

    @Test
    fun `an editor save reaches a second observer from the server's answer`() {
        withRepository { repository ->
            repository.refresh()
            val hub = repository.sliders
            answers["POST "] =
                FakeResponse(
                    headers = JSON,
                    body = listOf(sliderJson(3, 9), sliderJson(2, 5, enabled = false), sliderJson(1, 4)).joinToString(",", "[", "]"),
                )

            val saved = repository.save(ready(repository).sliders.map { it.copy(enabled = it.id != 2) }.reversed())

            assertEquals(listOf(3, 2, 1), saved.map { it.id })
            assertEquals(
                "the hub's view is the answer, not what was sent",
                listOf(3, 2, 1),
                (hub.value as SliderListState.Ready).sliders.map { it.id },
            )
            assertEquals(false, (hub.value as SliderListState.Ready).sliders.first { it.id == 2 }.enabled)
        }
    }

    @Test
    fun `an added slider reaches the list where the server put it`() {
        withRepository { repository ->
            repository.refresh()
            answers["POST /add"] =
                FakeResponse(headers = JSON, body = sliderJson(9, 13, enabled = false, title = "Anime", data = "210024", builtIn = false))
            server = listOf(sliderJson(9, 13, enabled = false, title = "Anime", data = "210024", builtIn = false)) + server

            val added = repository.add(SliderForm(type = SliderType.MovieKeyword, title = "Anime", data = "210024"))

            assertEquals(9, added.id)
            assertEquals(listOf(9, 1, 2, 3), ready(repository).sliders.map { it.id })
        }
    }

    @Test
    fun `an edited slider is replaced in place from the server's answer`() {
        withRepository { repository ->
            server = listOf(sliderJson(1, 4), sliderJson(7, 13, title = "Old", data = "1", builtIn = false))
            repository.refresh()
            answers["PUT /7"] = FakeResponse(headers = JSON, body = sliderJson(7, 13, title = "New", data = "2", builtIn = false))

            val form = repository.update(7, SliderForm(id = 7, type = SliderType.MovieKeyword, title = "New", data = "2"))

            assertEquals("New", form.title)
            assertEquals(listOf(1, 7), ready(repository).sliders.map { it.id })
            assertEquals("New", ready(repository).sliders.first { it.id == 7 }.title)
        }
    }

    @Test
    fun `a deleted slider leaves the list`() {
        withRepository { repository ->
            repository.refresh()
            answers["DELETE /2"] = FakeResponse(code = 204)

            repository.delete(2)

            assertEquals(listOf(1, 3), ready(repository).sliders.map { it.id })
        }
    }

    @Test
    fun `a reset is the call alone and the next refresh is what the observers see`() {
        withRepository { repository ->
            repository.refresh()
            answers["GET /reset"] = FakeResponse(headers = JSON, body = "{}")
            server = listOf(sliderJson(1, 4), sliderJson(2, 5))

            repository.reset()
            assertEquals(3, ready(repository).sliders.size)
            repository.refresh()

            assertEquals(listOf(1, 2), ready(repository).sliders.map { it.id })
        }
    }

    @Test
    fun `a reset the server refuses throws as the call did`() {
        withRepository { repository ->
            failing += "GET /reset"
            assertThrows(HttpException::class.java) { runBlocking { repository.reset() } }
        }
    }

    @Test
    fun `a failed refresh keeps the last good list and still throws`() {
        withRepository { repository ->
            repository.refresh()
            failing += "GET "

            assertThrows(HttpException::class.java) { runBlocking { repository.refresh() } }

            assertEquals(listOf(1, 2, 3), ready(repository).sliders.map { it.id })
        }
    }

    @Test
    fun `a first read that fails is a failure and not an empty list`() {
        withRepository { repository ->
            failing += "GET "

            assertThrows(HttpException::class.java) { runBlocking { repository.refresh() } }

            assertTrue(repository.sliders.value is SliderListState.Failed)
        }
    }

    @Test
    fun `a failed write leaves the list as it was`() {
        withRepository { repository ->
            repository.refresh()
            failing += "DELETE /2"

            assertThrows(HttpException::class.java) { runBlocking { repository.delete(2) } }

            assertEquals(listOf(1, 2, 3), ready(repository).sliders.map { it.id })
        }
    }

    @Test
    fun `a server with no slider API gets the default set and is not asked`() {
        profile = SeerrServerProfile(variant = SeerrVariant.Overseerr, version = SeerrVersion(1, 20, 0))
        withRepository { repository ->
            val list = repository.refresh()

            assertEquals(defaultSliders(), list)
            assertEquals(true, ready(repository).isDefault)
        }
        assertEquals(0, seerr.requestCount)
    }

    @Test
    fun `another server never shows the last one's list`() {
        val changes = MutableSharedFlow<Any?>(replay = 1)
        changes.tryEmit("server one")
        withRepository(changes) { repository ->
            repository.refresh()
            assertTrue(repository.sliders.value is SliderListState.Ready)

            changes.emit("server two")

            assertSame(SliderListState.Loading, repository.sliders.value)
        }
    }

    @Test
    fun `the server already saved is no change`() {
        val changes = MutableSharedFlow<Any?>(replay = 1)
        changes.tryEmit("server one")
        withRepository(changes) { repository ->
            repository.refresh()
            changes.emit("server one")

            assertTrue(repository.sliders.value is SliderListState.Ready)
        }
    }
}
