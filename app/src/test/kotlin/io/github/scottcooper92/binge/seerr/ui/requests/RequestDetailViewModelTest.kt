package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ADMIN = 2
private const val REQUEST = 32

/** The request page over a real connection into a path-scripted Seerr; Main is real-time, as for the hub. */
class RequestDetailViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val received = mutableListOf<RecordedRequest>()
    private val responses = mutableMapOf<String, () -> MockResponse>()
    private val viewModels = ViewModelStore()
    private var stores = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return responses[request.url.encodedPath]?.invoke() ?: MockResponse(code = 404)
                }
            }
        seerr.start()
    }

    /**
     * Main is set on every setup and never reset: a callback still in flight at teardown would
     * otherwise dispatch into the unset window and be reported into whichever test runs next.
     */
    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private fun serve(
        path: String,
        body: String,
    ) {
        responses[path] = { MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body) }
    }

    private fun server(permissions: Int) {
        serve("/api/v1/auth/me", """{"id":7,"displayName":"Scott","permissions":$permissions}""")
        serve("/api/v1/status", """{"version":"3.1.0"}""")
        serve("/api/v1/settings/public", """{"mediaServerType":2}""")
        serve(
            "/api/v1/request/11",
            """{"id":11,"status":2,"createdAt":"2026-06-01T10:00:00.000Z","updatedAt":"2026-06-02T10:00:00.000Z",
               "requestedBy":{"displayName":"scott"},"modifiedBy":{"displayName":"admin"},"serverId":1,"profileId":4,"rootFolder":"/tv","tags":[2],
               "seasons":[{"seasonNumber":1,"status":5},{"seasonNumber":2,"status":3}],
               "media":{"id":900,"tmdbId":200,"mediaType":"tv","status":4,"mediaUrl":"https://jellyfin.example.com/item/1",
                 "downloadStatus":[{"title":"Severance.S02","size":1000,"sizeLeft":250,"status":"downloading","timeLeft":"00:10:00"}]}}""",
        )
        serve(
            "/api/v1/tv/200",
            """{"name":"Severance","posterPath":"/sev.jpg","backdropPath":"/sev-bd.jpg","overview":"Work-life balance.","firstAirDate":"2022-02-18",
            "seasons":[{"seasonNumber":1,"name":"Season 1","episodeCount":9},{"seasonNumber":2,"name":"Season 2","episodeCount":10}]}""",
        )
        serve("/api/v1/service/sonarr", """[{"id":1,"name":"Sonarr","isDefault":true}]""")
        serve(
            "/api/v1/service/sonarr/1",
            """{"server":{"id":1,"name":"Sonarr"},"profiles":[{"id":4,"name":"HD-1080p"}],"rootFolders":[],"tags":[{"id":2,"label":"family"}]}""",
        )
        serve("/api/v1/issue", """{"id":5}""")
    }

    private suspend fun TestScope.connection(): SeerrConnection {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("d${stores++}.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false),
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        return connection
    }

    private fun TestScope.viewModel(
        connection: SeerrConnection,
        requestId: Int = 11,
    ): RequestDetailViewModel {
        val vm = RequestDetailViewModel(connection, TitleCache(), requestId)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun TestScope.viewModel(requestId: Int = 11): RequestDetailViewModel = viewModel(connection(), requestId)

    private suspend fun RequestDetailViewModel.awaitReady(
        match: (RequestDetailUiState.Ready) -> Boolean = {
            true
        },
    ): RequestDetailUiState.Ready = uiState.first { it is RequestDetailUiState.Ready && match(it) } as RequestDetailUiState.Ready

    @Test
    fun `a show's request reads as its title, history, seasons, destination and downloads`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()

            val detail = vm.awaitReady().detail

            assertEquals("Severance", detail.item.title)
            assertEquals("https://image.tmdb.org/t/p/w1280/sev-bd.jpg", detail.backdropUrl)
            assertEquals("Work-life balance.", detail.overview)
            assertEquals("admin", detail.modifiedBy)
            assertEquals(
                listOf(
                    SeasonState(1, "Season 1", 9, SeerrMediaStatusCode.Available),
                    SeasonState(2, "Season 2", 10, SeerrMediaStatusCode.Processing),
                ),
                detail.seasons,
            )
            assertEquals(RequestDestination("Sonarr", "HD-1080p", "/tv", listOf("family")), detail.destination)
            val download = detail.downloads.single()
            assertEquals("Severance.S02", download.title)
            assertEquals(0.75f, download.fraction)
            assertEquals(10, download.etaMinutes)
            assertEquals(900, detail.mediaId)
            assertTrue(detail.canReportIssue)
            assertEquals(seerr.url("/").toString() + "tv/200", detail.webUrl)
            assertEquals("https://jellyfin.example.com/item/1", detail.mediaServerUrl)
        }

    @Test
    fun `reporting an issue posts against the server's media id, and a plain user without the permission is not offered it`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()
            vm.awaitReady()

            vm.reportIssue(IssueType.Subtitles, " Missing subs ")

            assertEquals(IssueReport.Sent, vm.awaitReady { it.report == IssueReport.Sent }.report)
            val posted =
                received
                    .last { it.url.encodedPath == "/api/v1/issue" }
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue(posted.contains("\"mediaId\":900"))
            assertTrue(posted.contains("\"issueType\":3"))
            assertTrue(posted.contains("\"message\":\"Missing subs\""))

            responses["/api/v1/auth/me"] =
                {
                    MockResponse(
                        code = 200,
                        headers = headersOf("Content-Type", "application/json"),
                        body = """{"id":8,"permissions":$REQUEST}""",
                    )
                }
            val plain = viewModel()
            assertFalse(plain.awaitReady().detail.canReportIssue)
        }

    @Test
    fun `a request still shows, with no actions offered, when the user lookup used for moderation fails`() =
        runTest {
            server(ADMIN)
            val connection = connection()
            responses["/api/v1/auth/me"] = { MockResponse(code = 500) }
            val vm = viewModel(connection)

            val detail = vm.awaitReady().detail

            assertEquals(RequestActions(), detail.actions)
            assertFalse(detail.canReportIssue)
        }

    @Test
    fun `a request the server no longer has reads as not found, and one whose destination is gone still shows`() =
        runTest {
            server(ADMIN)
            responses.remove("/api/v1/service/sonarr/1")
            val vm = viewModel()
            val detail = vm.awaitReady().detail
            assertEquals(RequestDestination("Sonarr", null, "/tv", emptyList()), detail.destination)

            val missing = viewModel(requestId = 99)
            val error = missing.uiState.first { it is RequestDetailUiState.Error } as RequestDetailUiState.Error
            assertEquals(SeerrError.NotFound, error.error)
            assertNull(responses["/api/v1/request/99"])
        }

    /** The request pending, requested by user 8, and its show with two more seasons: one the server has, one another request covers. */
    private fun editable() {
        serve(
            "/api/v1/request/11",
            """{"id":11,"status":1,"createdAt":"2026-06-01T10:00:00.000Z","requestedBy":{"id":8,"displayName":"scott"},
               "serverId":1,"profileId":4,"rootFolder":"/tv","tags":[2],
               "seasons":[{"seasonNumber":1,"status":2},{"seasonNumber":2,"status":2}],
               "media":{"id":900,"tmdbId":200,"mediaType":"tv","status":2}}""",
        )
        serve(
            "/api/v1/tv/200",
            """{"name":"Severance","firstAirDate":"2022-02-18",
            "mediaInfo":{"id":900,"status":4,"seasons":[{"seasonNumber":3,"status":5}],
              "requests":[{"id":11,"status":1,"seasons":[{"seasonNumber":1},{"seasonNumber":2}]},{"id":12,"status":2,"seasons":[{"seasonNumber":5}]}]},
            "seasons":[{"seasonNumber":0,"name":"Specials","episodeCount":3},{"seasonNumber":1,"name":"Season 1","episodeCount":9},
              {"seasonNumber":2,"name":"Season 2","episodeCount":10},{"seasonNumber":3,"name":"Season 3","episodeCount":8},
              {"seasonNumber":4,"name":"Season 4","episodeCount":0},{"seasonNumber":5,"name":"Season 5","episodeCount":6}]}""",
        )
        serve(
            "/api/v1/service/sonarr/1",
            """{"server":{"id":1,"name":"Sonarr"},"profiles":[{"id":4,"name":"HD-1080p"},{"id":6,"name":"Any"}],
               "rootFolders":[{"id":1,"path":"/tv"},{"id":2,"path":"/kids"}],"tags":[{"id":2,"label":"family"},{"id":3,"label":"kids"}]}""",
        )
    }

    @Test
    fun `the editor ticks the request's seasons, locks the ones held elsewhere, and saves the new set with the destination`() =
        runTest {
            server(ADMIN)
            editable()
            val vm = viewModel()
            assertTrue(vm.awaitReady().detail.canEdit)

            vm.startEdit()
            val opened = vm.awaitReady { it.edit?.destination?.loadingChoices == false }
            val edit = checkNotNull(opened.edit)
            assertEquals(
                listOf(
                    SeasonChoice(1, "Season 1", 9, selected = true),
                    SeasonChoice(2, "Season 2", 10, selected = true),
                    SeasonChoice(3, "Season 3", 8, selected = false, heldStatus = SeerrMediaStatusCode.Available),
                    SeasonChoice(5, "Season 5", 6, selected = false, heldStatus = SeerrMediaStatusCode.Pending),
                ),
                edit.seasons,
            )
            val destination = checkNotNull(edit.destination)
            assertEquals(1, destination.serverId)
            assertEquals(4, destination.profileId)
            assertEquals("/tv", destination.rootFolder)
            assertEquals(listOf("/tv", "/kids"), destination.rootFolders)
            assertEquals(setOf(2), destination.tagIds)

            vm.editor.toggleSeason(2)
            vm.editor.toggleSeason(3)
            vm.editor.toggleTag(3)
            vm.editor.selectProfile(6)
            vm.editor.selectRootFolder("/kids")
            val changed =
                checkNotNull(
                    vm
                        .awaitReady {
                            it.edit
                                ?.seasons
                                ?.get(1)
                                ?.selected == false
                        }.edit,
                )
            assertTrue(changed.seasons[2].locked)
            assertTrue(changed.canSave)

            vm.editor.save()

            vm.awaitReady { it.edit == null }
            val put = received.last { it.method == "PUT" && it.url.encodedPath == "/api/v1/request/11" }
            val body = put.body?.utf8().orEmpty()
            assertTrue(body, body.contains("\"mediaType\":\"tv\""))
            assertTrue(body, body.contains("\"seasons\":[1]"))
            assertTrue(body, body.contains("\"serverId\":1"))
            assertTrue(body, body.contains("\"profileId\":6"))
            assertTrue(body, body.contains("\"rootFolder\":\"/kids\""))
            assertTrue(body, body.contains("\"tags\":[2,3]"))
        }

    @Test
    fun `a requester without advanced requests edits only the seasons of their own pending request`() =
        runTest {
            server(REQUEST)
            editable()
            serve("/api/v1/auth/me", """{"id":8,"displayName":"Scott","permissions":$REQUEST}""")
            val vm = viewModel()
            val detail = vm.awaitReady().detail
            assertTrue(detail.canEdit)
            assertFalse(detail.canEditDestination)

            vm.startEdit()
            val edit = checkNotNull(vm.awaitReady { it.edit != null }.edit)
            assertNull(edit.destination)
            vm.editor.toggleSeason(1)
            vm.editor.toggleSeason(2)
            assertFalse(checkNotNull(vm.awaitReady { it.edit?.seasons?.none { s -> s.selected } == true }.edit).canSave)
            vm.editor.cancel()
            assertNull(vm.awaitReady { it.edit == null }.edit)
            assertTrue(received.none { it.method == "PUT" })

            serve("/api/v1/auth/me", """{"id":9,"displayName":"Other","permissions":$REQUEST}""")
            assertFalse(viewModel().awaitReady().detail.canEdit)
        }

    @Test
    fun `a moderator manages the media record, with watch data where the server has it, and a plain user only reads it`() =
        runTest {
            server(ADMIN)
            serve(
                "/api/v1/media/900/watch_data",
                """{"data":{"playCount":12,"playCount7Days":2,"playCount30Days":5,"users":[{"displayName":"Scott"},{"username":"ana"}]}}""",
            )
            serve("/api/v1/media/900/available", "{}")
            serve("/api/v1/media/900/file", "{}")
            serve("/api/v1/media/900", "{}")
            val vm = viewModel()
            val media = checkNotNull(vm.awaitReady().detail.media)

            assertEquals(900, media.mediaId)
            assertTrue(media.isTv)
            assertTrue(media.canSetStatus)
            assertTrue(media.canClearData)
            assertTrue(media.canDeleteFiles)
            val standard = media.instances.single()
            assertFalse(standard.is4k)
            assertEquals(SeerrMediaStatusCode.PartiallyAvailable, standard.status)
            assertEquals(WatchStats(12, 2, 5, listOf("Scott", "ana")), standard.watch)

            vm.moderation.setMediaStatus(11, 900, MediaStatusChoice.Available, is4k = false)
            vm.moderation.events.first { it == ModerationEvent.MediaStatusSet }
            vm.moderation.deleteMediaFiles(11, 900, is4k = false)
            vm.moderation.events.first { it == ModerationEvent.MediaFilesDeleted }
            vm.moderation.clearMedia(11, 900)
            vm.moderation.events.first { it == ModerationEvent.MediaCleared }

            val status = received.first { it.method == "POST" && it.url.encodedPath == "/api/v1/media/900/available" }
            assertEquals("false", status.url.queryParameter("is4k"))
            val files = received.first { it.method == "DELETE" && it.url.encodedPath == "/api/v1/media/900/file" }
            assertEquals("false", files.url.queryParameter("is4k"))
            assertTrue(received.any { it.method == "DELETE" && it.url.encodedPath == "/api/v1/media/900" })

            serve("/api/v1/auth/me", """{"id":8,"permissions":$REQUEST}""")
            val watchReads = received.count { it.url.encodedPath == "/api/v1/media/900/watch_data" }
            val plain = checkNotNull(viewModel().awaitReady().detail.media)
            assertFalse(plain.canManage)
            assertNull(plain.instances.single().watch)
            assertEquals(watchReads, received.count { it.url.encodedPath == "/api/v1/media/900/watch_data" })
        }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
