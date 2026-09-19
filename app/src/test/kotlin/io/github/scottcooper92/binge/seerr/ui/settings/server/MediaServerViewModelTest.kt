package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val PLEX =
    """{"name":"Home","machineId":"m1","ip":"plex.local","port":32400,"useSsl":true,"webAppUrl":"https://app.plex.tv",
       "libraries":[{"id":"1","name":"Movies","enabled":true,"type":"movie","lastScan":1700000000000},
                    {"id":"2","name":"Shows","enabled":false,"type":"show"}]}"""

private const val JELLYFIN =
    """{"name":"Media","ip":"jelly.local","port":8096,"useSsl":false,"urlBase":"/jf","externalHostname":"https://jelly.example",
       "jellyfinForgotPasswordUrl":"https://jelly.example/forgot","serverId":"s1","apiKey":"jf-key",
       "libraries":[{"id":"a","name":"Films","enabled":true,"type":"movie"}]}"""

private const val IDLE = """{"running":false,"progress":0,"total":0}"""

/** Long enough for a second call to reach the lock while the first still holds it. */
private const val LIBRARY_HOLD_MILLIS = 300L

/**
 * The released `GET /library` as Overseerr implements it: every library's `enabled` is rewritten
 * from the `enable` parameter on every call, outside the `sync` branch and unguarded, so an absent
 * parameter disables all of them. `3` is the library a sync discovers.
 */
private fun libraryRoute(request: RecordedRequest): String {
    val enabled =
        request.url
            .queryParameter("enable")
            ?.split(",")
            .orEmpty()
    val ids = if (request.url.queryParameter("sync") == "true") listOf("1", "2", "3") else listOf("1", "2")
    return ids.joinToString(",", "[", "]") { id ->
        """{"id":"$id","name":"L$id","enabled":${id in enabled},"type":"movie"}"""
    }
}

class MediaServerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        seerr.start()
        seerr.serve("GET /api/v1/settings/plex", PLEX)
        seerr.serve("POST /api/v1/settings/plex", PLEX)
        seerr.serve("GET /api/v1/settings/plex/sync", IDLE)
        seerr.serve("GET /api/v1/settings/jellyfin", JELLYFIN)
        seerr.serve("GET /api/v1/settings/jellyfin/sync", IDLE)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private fun plexServer() = seerr.viewer(id = 1, permissions = ADMIN, settings = """{"mediaServerType":1}""")

    private suspend fun TestScope.viewModel(): MediaServerViewModel {
        val vm = MediaServerViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
        vm.scanPollMillis = 10
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun MediaServerViewModel.awaitReady(
        where: (ExtrasEditorUiState.Ready<MediaServerForm, MediaServerExtras>) -> Boolean = { true },
    ): ExtrasEditorUiState.Ready<MediaServerForm, MediaServerExtras> =
        uiState.first {
            it is ExtrasEditorUiState.Ready && !it.saving && where(it)
        } as ExtrasEditorUiState.Ready<MediaServerForm, MediaServerExtras>

    @Test
    fun `a plex server reads its record and libraries, with no jellyfin-only fields`() =
        runTest {
            plexServer()
            val vm = viewModel()
            val draft = vm.awaitReady().draft

            assertEquals(MediaServerKind.Plex, draft.kind)
            assertEquals("Home", draft.serverName)
            assertEquals("plex.local", draft.host)
            assertEquals("32400", draft.port)
            assertTrue(draft.useSsl)
            assertEquals("https://app.plex.tv", draft.externalUrl)
            assertNull(draft.urlBase)
            assertNull(draft.apiKey)
            val extras = vm.awaitReady { it.extras.libraries.isNotEmpty() }.extras
            assertEquals(listOf("Movies", "Shows"), extras.libraries.map { it.name })
            assertEquals(LibraryType.Movies, extras.libraries[0].type)
            assertEquals(1_700_000_000_000L, extras.libraries[0].lastScanMillis)
            assertFalse(extras.libraries[1].enabled)
            assertEquals(false, extras.scan?.running)
        }

    @Test
    fun `a jellyfin server reads the jellyfin record, with its own fields`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val draft = viewModel().awaitReady().draft

            assertEquals(MediaServerKind.Jellyfin, draft.kind)
            assertEquals("jelly.local", draft.host)
            assertEquals("/jf", draft.urlBase)
            assertEquals("https://jelly.example", draft.externalUrl)
            assertEquals("https://jelly.example/forgot", draft.forgotPasswordUrl)
            assertEquals("jf-key", draft.apiKey)
        }

    @Test
    fun `saving posts the connection alone, never the libraries`() =
        runTest {
            plexServer()
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(port = "32401", useSsl = false) }
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/plex")).jsonObject
            assertEquals("plex.local", sent.getValue("ip").jsonPrimitive.content)
            assertEquals("32401", sent.getValue("port").jsonPrimitive.content)
            assertEquals("false", sent.getValue("useSsl").jsonPrimitive.content)
            assertNull(sent["libraries"])
        }

    @Test
    fun `a bad port blocks the save`() =
        runTest {
            plexServer()
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(port = "99999") }
            vm.save()

            assertEquals(0, seerr.count("POST", "/api/v1/settings/plex"))
        }

    /** A released server has no per-library route: the 404 falls back to the whole enabled set on the GET. */
    @Test
    fun `a library toggle falls back to the enable query on a released server`() =
        runTest {
            plexServer()
            val vm = viewModel()
            vm.awaitReady()
            vm.awaitReady { it.extras.libraries.isNotEmpty() }
            seerr.serve(
                "GET /api/v1/settings/plex/library",
                PLEX.substringAfter("\"libraries\":").dropLast(1).replace("\"enabled\":false", "\"enabled\":true"),
            )

            vm.setLibraryEnabled("2", true)
            val extras =
                vm
                    .awaitReady { it.extras.libraries.all { library -> library.enabled } && it.extras.busyLibraryIds.isEmpty() }
                    .extras

            assertEquals(1, seerr.count("PUT", "/api/v1/settings/plex/library/2"))
            val read = seerr.received.last { it.url.encodedPath == "/api/v1/settings/plex/library" }
            assertEquals("1,2", read.url.queryParameter("enable"))
            assertTrue(extras.busyLibraryIds.isEmpty())
        }

    @Test
    fun `a library toggle uses the per-library route where the server has it`() =
        runTest {
            plexServer()
            seerr.serve("PUT /api/v1/settings/plex/library/2", """{"id":"2","name":"Shows","enabled":true,"type":"show"}""")
            val vm = viewModel()
            vm.awaitReady()
            vm.awaitReady { it.extras.libraries.isNotEmpty() }

            vm.setLibraryEnabled("2", true)
            val extras = vm.awaitReady { it.extras.libraries.all { library -> library.enabled } }.extras

            assertEquals("""{"enabled":true}""", seerr.body("PUT", "/api/v1/settings/plex/library/2"))
            assertEquals(0, seerr.count("GET", "/api/v1/settings/plex/library"))
            assertEquals(LibraryType.Movies, extras.libraries.first { it.id == "1" }.type)
        }

    @Test
    fun `syncing libraries uses the sync route where the server has it`() =
        runTest {
            plexServer()
            seerr.serve(
                "POST /api/v1/settings/plex/library/sync",
                """[{"id":"1","name":"Movies","enabled":true,"type":"movie"},
                   {"id":"3","name":"Music","enabled":false,"type":"movie"}]""",
            )
            val vm = viewModel()
            vm.awaitReady()
            vm.awaitReady { it.extras.libraries.isNotEmpty() }

            vm.syncLibraries()
            val extras = vm.awaitReady { it.extras.libraries.any { library -> library.id == "3" } }.extras

            assertEquals(0, seerr.count("GET", "/api/v1/settings/plex/library"))
            assertEquals(listOf("1", "3"), extras.libraries.map { it.id })
        }

    /**
     * A released server has no `library/sync` route: the 404 falls back to `sync=true` on the GET.
     *
     * That route rewrites every library's `enabled` from the `enable` parameter on every call, so
     * the sync has to carry the enabled ids or it disables them all — the fake answers the same way,
     * which is what lets the wipe show up here rather than only on a real server.
     */
    @Test
    fun `syncing libraries falls back to the sync query, carrying the enabled ids`() =
        runTest {
            plexServer()
            seerr.serveFrom("GET /api/v1/settings/plex/library") { request -> libraryRoute(request) }
            val vm = viewModel()
            vm.awaitReady()
            vm.awaitReady { it.extras.libraries.isNotEmpty() }

            vm.syncLibraries()
            val extras = vm.awaitReady { it.extras.libraries.any { library -> library.id == "3" } }.extras

            assertEquals(1, seerr.count("GET", "/api/v1/settings/plex/library"))
            val read = seerr.received.last { it.url.encodedPath == "/api/v1/settings/plex/library" }
            assertEquals("true", read.url.queryParameter("sync"))
            assertEquals("1", read.url.queryParameter("enable"))
            assertEquals(listOf("1", "2", "3"), extras.libraries.map { it.id })
            assertEquals(listOf("1"), extras.libraries.filter { it.enabled }.map { it.id })
        }

    /**
     * A sync started while a toggle is still in flight. Both are whole-set writes on the released
     * path, so the sync has to read its enabled set after the toggle has folded its result;
     * otherwise it sends the set from before the toggle and the server undoes it.
     *
     * The route is held back so the sync queues behind the toggle rather than following it.
     */
    @Test
    fun `a sync overlapping a toggle does not undo the toggle`() =
        runTest {
            plexServer()
            seerr.serveFrom("GET /api/v1/settings/plex/library", delayMillis = LIBRARY_HOLD_MILLIS) { libraryRoute(it) }
            val vm = viewModel()
            vm.awaitReady()
            vm.awaitReady { it.extras.libraries.isNotEmpty() }

            vm.setLibraryEnabled("2", enabled = true)
            seerr.awaitCount("GET", "/api/v1/settings/plex/library", moreThan = 0)
            vm.syncLibraries()
            val extras = vm.awaitReady { it.extras.libraries.any { library -> library.id == "3" } }.extras

            val sync = seerr.received.last { it.url.encodedPath == "/api/v1/settings/plex/library" }
            assertEquals("1,2", sync.url.queryParameter("enable"))
            assertEquals(listOf("1", "2"), extras.libraries.filter { it.enabled }.map { it.id })
        }

    @Test
    fun `a full scan is started with the command body and followed until it stops`() =
        runTest {
            plexServer()
            val vm = viewModel()
            vm.awaitReady()
            seerr.serve("POST /api/v1/settings/plex/sync", """{"running":true,"progress":0,"total":2}""")
            seerr.serve(
                "GET /api/v1/settings/plex/sync",
                """{"running":true,"progress":1,"total":2,"currentLibrary":{"id":"1","name":"Movies"}}""",
            )

            vm.startScan()
            val midway = vm.awaitReady { it.extras.scan?.progress == 1 }.extras
            assertEquals("Movies", midway.scan?.currentLibrary)
            assertEquals("""{"start":true}""", seerr.body("POST", "/api/v1/settings/plex/sync"))

            seerr.serve("GET /api/v1/settings/plex/sync", """{"running":false,"progress":2,"total":2}""")
            val done = vm.awaitReady { it.extras.scan?.running == false }.extras
            assertEquals(2, done.scan?.progress)
        }

    @Test
    fun `the picker offers only the admin's own servers, and a pick fills the address`() =
        runTest {
            plexServer()
            seerr.serve(
                "GET /api/v1/settings/plex/devices/servers",
                """[{"name":"Home","clientIdentifier":"abc","owned":true,"connection":[
                      {"protocol":"https","address":"1.2.3.4","port":32400,"local":false,"status":200},
                      {"protocol":"http","address":"192.168.1.5","port":32400,"local":true,"status":500,"message":"timeout"}]},
                    {"name":"A friend's","clientIdentifier":"def","owned":false,"connection":[]}]""",
            )
            val vm = viewModel()
            vm.awaitReady()

            vm.openServerPicker()
            val picker = vm.awaitReady { it.extras.picker is PlexServerPicker.Ready }.extras.picker as PlexServerPicker.Ready
            assertEquals(listOf("Home"), picker.servers.map { it.name })
            assertEquals(
                listOf(true, false),
                picker.servers
                    .single()
                    .connections
                    .map { it.reachable },
            )

            val server = picker.servers.single()
            vm.chooseConnection(server, server.connections.first())
            val draft = vm.awaitReady().draft
            assertEquals("1.2.3.4", draft.host)
            assertEquals("32400", draft.port)
            assertTrue(draft.useSsl)
            assertNull(vm.awaitReady().extras.picker)
        }
}
