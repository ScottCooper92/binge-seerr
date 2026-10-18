package com.cooper.binge.companion.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.AdvancedRequest
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val MOVIE = AdvancedRequest(MediaType.MEDIA_TYPE_MOVIE, tmdbId = 603, seasonNumbers = emptyList(), versionId = "")
private val SHOW = AdvancedRequest(MediaType.MEDIA_TYPE_TV, tmdbId = 1399, seasonNumbers = listOf(1, 2), versionId = "")

private const val SERVERS = """[
  {"id":1,"name":"Main","is4k":false,"isDefault":true,"activeProfileId":4,"activeDirectory":"/media"},
  {"id":2,"name":"Spare","is4k":false},
  {"id":3,"name":"4K","is4k":true,"isDefault":true}
]"""
private const val DETAILS = """{
  "server":{"id":1},
  "profiles":[{"id":4,"name":"HD"},{"id":5,"name":"UHD"}],
  "rootFolders":[{"id":1,"path":"/media"},{"id":2,"path":"/kids"}]
}"""

/** The hand-off's state over an in-memory connection into a scripted server, as the setup screen's test does. */
class AdvancedRequestViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()

    /**
     * No [androidx.lifecycle.ViewModelStore] here - this file never clears its view models - but
     * a call the test never awaited can still be resuming on OkHttp's own thread when the test
     * method returns, so draining before `MainDispatcherRule` resets Main matters just the same (#177).
     */
    @After
    fun tearDown() = seerr.awaitIdle()

    @Test
    fun `the picker opens on the default server of the title's kind, with that server's defaults`() =
        runTest {
            seerr.enqueue(json(SERVERS))
            seerr.enqueue(json(DETAILS))

            val ready = viewModel(MOVIE).awaitChoices()

            assertEquals("/api/v1/service/radarr", seerr.takeRequest().url.encodedPath)
            assertEquals("/api/v1/service/radarr/1", seerr.takeRequest().url.encodedPath)
            assertEquals(listOf(Choice(1, "Main"), Choice(2, "Spare")), ready.destination.servers)
            assertEquals(1, ready.destination.serverId)
            assertEquals(4, ready.destination.profileId)
            assertEquals("/media", ready.destination.rootFolder)
            assertEquals(listOf("/media", "/kids"), ready.destination.rootFolders)
        }

    @Test
    fun `a submit posts the overrides and finishes as submitted`() =
        runTest {
            seerr.enqueue(json(SERVERS))
            seerr.enqueue(json(DETAILS))
            seerr.enqueue(json("""{"id":9}""", code = 201))
            val vm = viewModel(MOVIE)
            vm.awaitChoices()
            vm.selectProfile(5)
            vm.selectRootFolder("/kids")

            vm.submit()

            vm.uiState.first { it is AdvancedRequestUiState.Submitted }
            seerr.takeRequest()
            seerr.takeRequest()
            val posted = seerr.takeRequest()
            assertEquals("/api/v1/request", posted.url.encodedPath)
            val body = posted.body
            assertTrue(body, body.contains("\"mediaType\":\"movie\""))
            assertTrue(body, body.contains("\"mediaId\":603"))
            assertTrue(body, body.contains("\"serverId\":1"))
            assertTrue(body, body.contains("\"profileId\":5"))
            assertTrue(body, body.contains("\"rootFolder\":\"/kids\""))
            assertFalse(body, body.contains("seasons"))
        }

    @Test
    fun `a show goes to sonarr and carries the seasons the host picked`() =
        runTest {
            seerr.enqueue(json(SERVERS))
            seerr.enqueue(json(DETAILS))
            seerr.enqueue(json("""{"id":10}""", code = 201))
            val vm = viewModel(SHOW)
            vm.awaitChoices()

            vm.submit()

            vm.uiState.first { it is AdvancedRequestUiState.Submitted }
            assertEquals("/api/v1/service/sonarr", seerr.takeRequest().url.encodedPath)
            assertEquals("/api/v1/service/sonarr/1", seerr.takeRequest().url.encodedPath)
            val body = seerr.takeRequest().body
            assertTrue(body, body.contains("\"mediaType\":\"tv\""))
            assertTrue(body, body.contains("\"seasons\":[1,2]"))
        }

    @Test
    fun `switching server reloads its choices and re-seeds the defaults`() =
        runTest {
            seerr.enqueue(json(SERVERS))
            seerr.enqueue(json(DETAILS))
            seerr.enqueue(json("""{"profiles":[{"id":7,"name":"Any"}],"rootFolders":[{"id":3,"path":"/spare"}]}"""))
            val vm = viewModel(MOVIE)
            vm.awaitChoices()

            vm.selectServer(2)

            val ready = vm.awaitChoices { it.destination.serverId == 2 }
            assertEquals(7, ready.destination.profileId)
            assertEquals("/spare", ready.destination.rootFolder)
        }

    @Test
    fun `without a saved server the picker cannot open`() =
        runTest {
            val state = viewModel(MOVIE, connected = false).uiState.first { it is AdvancedRequestUiState.Failed }

            assertEquals(AdvancedRequestError.NotConnected, (state as AdvancedRequestUiState.Failed).error)
            assertEquals(0, seerr.requestCount)
        }

    @Test
    fun `a title with no server of its resolution is reported, not offered`() =
        runTest {
            seerr.enqueue(json("""[{"id":3,"name":"4K","is4k":true}]"""))

            val state = viewModel(MOVIE).uiState.first { it is AdvancedRequestUiState.Failed }

            assertEquals(AdvancedRequestError.NoServers, (state as AdvancedRequestUiState.Failed).error)
        }

    @Test
    fun `the 4K version id offers only the 4K servers`() =
        runTest {
            seerr.enqueue(json(SERVERS))
            seerr.enqueue(json(DETAILS))

            val state = viewModel(MOVIE.copy(versionId = "4k")).uiState.first { it is AdvancedRequestUiState.Ready }

            assertEquals(3, (state as AdvancedRequestUiState.Ready).destination.serverId)
        }

    @Test
    fun `a rejected submit stays on the form with the error`() =
        runTest {
            seerr.enqueue(json(SERVERS))
            seerr.enqueue(json(DETAILS))
            seerr.enqueue(FakeResponse(code = 403))
            val vm = viewModel(MOVIE)
            vm.awaitChoices()

            vm.submit()

            val ready = vm.awaitChoices { it.error != null }
            assertEquals(AdvancedRequestError.Rejected, ready.error)
            assertFalse(ready.isSubmitting)
        }

    private suspend fun TestScope.viewModel(
        request: AdvancedRequest,
        connected: Boolean = true,
    ): AdvancedRequestViewModel {
        val store =
            CredentialStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                PlainCipher,
            )
        if (connected) store.save(SeerrCredentials(seerr.url("/"), SeerrAuth.ApiKey("k3y"), SeerrVariant.Seerr))
        val vm =
            AdvancedRequestViewModel(
                SeerrConnection(
                    store,
                    SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
                ),
                mainDispatcherRule.dispatcher,
                request,
            )
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun AdvancedRequestViewModel.awaitChoices(
        match: (AdvancedRequestUiState.Ready) -> Boolean = { true },
    ): AdvancedRequestUiState.Ready =
        uiState.first { it is AdvancedRequestUiState.Ready && !it.destination.loadingChoices && match(it) } as AdvancedRequestUiState.Ready

    private fun json(
        body: String,
        code: Int = 200,
    ): FakeResponse = FakeResponse(code = code, headers = headersOf("Content-Type", "application/json"), body = body)
}
