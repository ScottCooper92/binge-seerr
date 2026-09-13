package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class MediaServerViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
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
        val vm = MediaServerViewModel(seerr.connection(this))
        vm.scanPollMillis = 10
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun MediaServerViewModel.awaitReady(): EditorUiState.Ready<MediaServerForm> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<MediaServerForm>

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
            val extras = vm.extras.first { it.libraries.isNotEmpty() }
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
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

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
            vm.extras.first { it.libraries.isNotEmpty() }
            seerr.serve(
                "GET /api/v1/settings/plex/library",
                PLEX.substringAfter("\"libraries\":").dropLast(1).replace("\"enabled\":false", "\"enabled\":true"),
            )

            vm.setLibraryEnabled("2", true)
            val extras = vm.extras.first { it.libraries.all { library -> library.enabled } && it.busyLibraryIds.isEmpty() }

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
            vm.extras.first { it.libraries.isNotEmpty() }

            vm.setLibraryEnabled("2", true)
            vm.extras.first { it.libraries.all { library -> library.enabled } }

            assertEquals("""{"enabled":true}""", seerr.body("PUT", "/api/v1/settings/plex/library/2"))
            assertEquals(0, seerr.count("GET", "/api/v1/settings/plex/library"))
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
            val midway = vm.extras.first { it.scan?.progress == 1 }
            assertEquals("Movies", midway.scan?.currentLibrary)
            assertEquals("""{"start":true}""", seerr.body("POST", "/api/v1/settings/plex/sync"))

            seerr.serve("GET /api/v1/settings/plex/sync", """{"running":false,"progress":2,"total":2}""")
            val done = vm.extras.first { it.scan?.running == false }
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
            val picker = vm.extras.first { it.picker is PlexServerPicker.Ready }.picker as PlexServerPicker.Ready
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
            assertNull(vm.extras.first().picker)
        }
}
