package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
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

private const val LINEAGE_MAIN =
    """{"apiKey":"old-key","applicationTitle":"Home","applicationUrl":"https://seerr.example","locale":"en",
       "discoverRegion":"GB","streamingRegion":"IE","originalLanguage":"","hideAvailable":true,"hideRequested":false,
       "partialRequestsEnabled":true,"enableSpecialEpisodes":true,"cacheImages":false,"youtubeUrl":"https://yt.example",
       "defaultPermissions":32}"""

private const val OVERSEERR_MAIN =
    """{"apiKey":"old-key","applicationTitle":"Home","region":"US","originalLanguage":"en","hideAvailable":false,
       "partialRequestsEnabled":false,"cacheImages":true,"trustProxy":true,"csrfProtection":false,"defaultPermissions":32}"""

class ServerGeneralViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()
    private lateinit var connection: SeerrConnection

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.serve("GET /api/v1/settings/main", LINEAGE_MAIN)
        seerr.serve("POST /api/v1/settings/main", LINEAGE_MAIN)
        seerr.serve("POST /api/v1/settings/main/regenerate", """{"apiKey":"new-key"}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): ServerGeneralViewModel {
        connection = seerr.connection(this)
        val vm = ServerGeneralViewModel(connection)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun ServerGeneralViewModel.awaitReady(): EditorUiState.Ready<ServerGeneralSettings> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<ServerGeneralSettings>

    @Test
    fun `the jellyseerr lineage shows both regions and its own switches, never the proxy ones`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            val draft = vm.awaitReady().draft

            assertEquals("Home", draft.applicationTitle)
            assertEquals("GB", draft.discoverRegion)
            assertEquals("IE", draft.streamingRegion)
            assertEquals(false, draft.hideRequested)
            assertEquals(true, draft.specialEpisodes)
            assertEquals("https://yt.example", draft.youtubeUrl)
            assertNull(draft.trustProxy)
            assertNull(draft.csrfProtection)
            assertNull(draft.versionCheck)
            val extras = vm.extras.first { it.visitor != null }
            assertEquals("old-key", extras.apiKey.key)
            assertFalse(extras.apiKey.revealed)
            assertEquals(true, extras.visitor?.localLogin)
        }

    @Test
    fun `overseerr shows one region and the proxy switches`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            seerr.serve("GET /api/v1/settings/main", OVERSEERR_MAIN)
            val draft = viewModel().awaitReady().draft

            assertEquals("US", draft.discoverRegion)
            assertNull(draft.streamingRegion)
            assertNull(draft.hideRequested)
            assertNull(draft.specialEpisodes)
            assertNull(draft.youtubeUrl)
            assertEquals(true, draft.trustProxy)
            assertEquals(false, draft.csrfProtection)
            assertEquals(false, draft.partialRequests)
        }

    @Test
    fun `saving posts only the form's fields, never the key, and adopts the answer`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()
            seerr.serve("POST /api/v1/settings/main", LINEAGE_MAIN.replace("\"Home\"", "\"Cinema\""))

            vm.edit { it.copy(applicationTitle = "Cinema", hideAvailable = false) }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/main")).jsonObject
            assertEquals("Cinema", sent.getValue("applicationTitle").jsonPrimitive.content)
            assertEquals("GB", sent.getValue("discoverRegion").jsonPrimitive.content)
            assertEquals("IE", sent.getValue("streamingRegion").jsonPrimitive.content)
            assertEquals("false", sent.getValue("hideAvailable").jsonPrimitive.content)
            assertNull(sent["apiKey"])
            assertNull(sent["defaultPermissions"])
            assertNull(sent["trustProxy"])
            val ready = vm.awaitReady()
            assertEquals("Cinema", ready.saved.applicationTitle)
            assertFalse(ready.dirty)
        }

    @Test
    fun `an address that is not a web url blocks the save`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(applicationUrl = "seerr.example") }
            vm.save()

            assertEquals(0, seerr.count("POST", "/api/v1/settings/main"))
        }

    /** The old key dies with the answer, so an app signed in with it must move to the new one in the same step. */
    @Test
    fun `regenerating reconnects an api-key session with the new key`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()

            vm.regenerateApiKey()
            assertTrue(vm.events.first() is EditorEvent.Notice)

            assertEquals(
                "new-key",
                vm.extras
                    .first { it.apiKey.key == "new-key" }
                    .apiKey.key,
            )
            assertEquals(SeerrAuth.ApiKey("new-key"), connection.current().auth)
            val probe = seerr.received.last { it.url.encodedPath == "/api/v1/auth/me" }
            assertEquals("new-key", probe.headers["X-Api-Key"])
        }
}
