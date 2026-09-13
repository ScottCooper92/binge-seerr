package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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

private const val TEST_RESULT =
    """{"profiles":[{"id":4,"name":"HD-1080p"},{"id":6,"name":"Ultra-HD"}],
        "rootFolders":[{"id":1,"path":"/movies"},{"id":2,"path":"/movies-4k"}],
        "tags":[{"id":1,"label":"binge"},{"id":2,"label":"kids"}]}"""

private const val SONARR_TEST_RESULT =
    """{"profiles":[{"id":5,"name":"HD"}],"rootFolders":[{"id":1,"path":"/tv"},{"id":3,"path":"/anime"}],
        "tags":[{"id":9,"label":"anime"}],"languageProfiles":[{"id":1,"name":"English"}]}"""

private const val SONARR =
    """[{"id":3,"name":"Main","hostname":"sonarr.local","port":8989,"apiKey":"s-key","useSsl":false,
         "activeProfileId":5,"activeProfileName":"HD","activeDirectory":"/tv","tags":[9],"is4k":false,"isDefault":true,
         "seriesType":"standard","animeSeriesType":"anime","activeAnimeProfileId":5,"activeAnimeDirectory":"/anime",
         "animeTags":[],"enableSeasonFolders":true,"activeLanguageProfileId":1,"monitorNewItems":"none"}]"""

class DvrInstanceViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/settings/radarr", "[]")
        seerr.serve("GET /api/v1/settings/sonarr", SONARR)
        seerr.serve("POST /api/v1/settings/radarr/test", TEST_RESULT)
        seerr.serve("POST /api/v1/settings/sonarr/test", SONARR_TEST_RESULT)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(
        type: ServiceType,
        id: Int?,
    ): DvrInstanceViewModel {
        val vm = DvrInstanceViewModel(seerr.connection(this), type, id)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun DvrInstanceViewModel.awaitReady(): EditorUiState.Ready<DvrForm> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<DvrForm>

    @Test
    fun `a new radarr starts on its port with nothing to pick from, and cannot be saved before a test`() =
        runTest {
            val vm = viewModel(ServiceType.Radarr, id = null)
            val draft = vm.awaitReady().draft
            assertEquals("7878", draft.port)
            assertEquals("released", draft.minimumAvailability)
            assertNull(draft.seriesType)
            assertNull(vm.extras.first().choices)
            assertEquals(0, seerr.count("POST", "/api/v1/settings/radarr/test"))

            vm.edit { it.copy(name = "Movies", host = "radarr.local", apiKey = "r-key") }
            assertFalse(vm.awaitReady().draft.valid)
            vm.save()
            assertEquals(0, seerr.count("POST", "/api/v1/settings/radarr"))
        }

    @Test
    fun `a test posts the typed connection and fills the pickers, taking the first of each`() =
        runTest {
            val vm = viewModel(ServiceType.Radarr, id = null)
            vm.awaitReady()
            vm.edit { it.copy(name = "Movies", host = "radarr.local", useSsl = true, apiKey = "r-key", baseUrl = "/radarr") }
            vm.test()

            assertEquals(EditorEvent.Notice(io.github.scottcooper92.binge.seerr.R.string.server_settings_dvr_tested), vm.events.first())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/radarr/test")).jsonObject
            assertEquals("radarr.local", sent.getValue("hostname").jsonPrimitive.content)
            assertEquals("7878", sent.getValue("port").jsonPrimitive.content)
            assertEquals("true", sent.getValue("useSsl").jsonPrimitive.content)
            assertEquals("/radarr", sent.getValue("baseUrl").jsonPrimitive.content)
            assertEquals("r-key", sent.getValue("apiKey").jsonPrimitive.content)

            val choices = vm.extras.first { it.choices != null }.choices
            assertEquals(listOf("HD-1080p", "Ultra-HD"), choices?.profiles?.map { it.label })
            assertEquals(listOf("/movies", "/movies-4k"), choices?.rootFolders)
            assertNull(choices?.languageProfiles)
            val draft = vm.awaitReady().draft
            assertEquals(4, draft.profileId)
            assertEquals("/movies", draft.rootFolder)
            assertTrue(vm.awaitReady().draft.valid)
        }

    @Test
    fun `saving posts the record with the profile's name and folder, and adopts the answer`() =
        runTest {
            seerr.serve(
                "POST /api/v1/settings/radarr",
                """{"id":7,"name":"Movies","hostname":"radarr.local","port":7878,"apiKey":"r-key","activeProfileId":6,
                    "activeProfileName":"Ultra-HD","activeDirectory":"/movies-4k","tags":[2],"is4k":true,"minimumAvailability":"inCinemas"}""",
            )
            val vm = viewModel(ServiceType.Radarr, id = null)
            vm.awaitReady()
            vm.edit { it.copy(name = "Movies", host = "radarr.local", apiKey = "r-key") }
            vm.test()
            assertEquals(EditorEvent.Notice(io.github.scottcooper92.binge.seerr.R.string.server_settings_dvr_tested), vm.events.first())
            vm.extras.first { it.choices != null }
            vm.edit { it.copy(profileId = 6, rootFolder = "/movies-4k", tagIds = setOf(2), is4k = true, minimumAvailability = "inCinemas") }
            // `events` has no replay, so subscribe before saving rather than after: subscribing
            // afterwards can miss the save's own event, or catch `test()`'s notice arriving late.
            val saved = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first { it is EditorEvent.Saved } }
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/radarr")).jsonObject
            assertEquals("6", sent.getValue("activeProfileId").jsonPrimitive.content)
            assertEquals("Ultra-HD", sent.getValue("activeProfileName").jsonPrimitive.content)
            assertEquals("/movies-4k", sent.getValue("activeDirectory").jsonPrimitive.content)
            assertEquals(listOf("2"), sent.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
            assertEquals("inCinemas", sent.getValue("minimumAvailability").jsonPrimitive.content)
            assertNull(sent["seriesType"])
            val ready = vm.awaitReady()
            assertEquals(7, ready.saved.id)
            assertFalse(ready.dirty)
        }

    @Test
    fun `an existing sonarr is read and tested on load, and a save puts to its id`() =
        runTest {
            seerr.serve("PUT /api/v1/settings/sonarr/3", SONARR.trim().removePrefix("[").removeSuffix("]"))
            val vm = viewModel(ServiceType.Sonarr, id = 3)
            val ready = vm.awaitReady()
            assertEquals("Main", ready.draft.name)
            assertEquals("anime", ready.draft.animeSeriesType)
            assertEquals("/anime", ready.draft.animeRootFolder)
            assertEquals(true, ready.draft.seasonFolders)
            assertEquals(1, ready.draft.languageProfileId)
            assertNull(ready.draft.minimumAvailability)
            assertEquals(
                listOf("English"),
                vm.extras
                    .first { it.choices != null }
                    .choices
                    ?.languageProfiles
                    ?.map { it.label },
            )
            assertEquals(1, seerr.count("POST", "/api/v1/settings/sonarr/test"))

            vm.edit { it.copy(seasonFolders = false) }
            // `events` has no replay, so subscribe before saving rather than after: subscribing
            // afterwards can miss the save's own event, or catch `test()`'s notice arriving late.
            val saved = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first { it is EditorEvent.Saved } }
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())
            val sent = Json.parseToJsonElement(seerr.body("PUT", "/api/v1/settings/sonarr/3")).jsonObject
            assertEquals("false", sent.getValue("enableSeasonFolders").jsonPrimitive.content)
            assertEquals("HD", sent.getValue("activeAnimeProfileName").jsonPrimitive.content)
            assertEquals("none", sent.getValue("monitorNewItems").jsonPrimitive.content)
        }

    @Test
    fun `a new sonarr defaults to monitoring all new seasons, matching the web client`() =
        runTest {
            seerr.serve(
                "POST /api/v1/settings/sonarr",
                SONARR.trim().removePrefix("[").removeSuffix("]"),
            )
            val vm = viewModel(ServiceType.Sonarr, id = null)
            assertEquals("all", vm.awaitReady().draft.monitorNewItems)

            vm.edit { it.copy(name = "Main", host = "sonarr.local", apiKey = "s-key") }
            vm.test()
            assertEquals(EditorEvent.Notice(io.github.scottcooper92.binge.seerr.R.string.server_settings_dvr_tested), vm.events.first())
            vm.extras.first { it.choices != null }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/sonarr")).jsonObject
            assertEquals("all", sent.getValue("monitorNewItems").jsonPrimitive.content)
        }

    @Test
    fun `saving an unrelated edit when the load-time test failed keeps the stored profile names`() =
        runTest {
            val recordWithAnimeName =
                SONARR.trim().removePrefix("[").removeSuffix("]").replace(
                    "\"activeAnimeProfileId\":5,",
                    "\"activeAnimeProfileId\":5,\"activeAnimeProfileName\":\"HD\",",
                )
            seerr.serve("GET /api/v1/settings/sonarr", "[$recordWithAnimeName]")
            seerr.serve("POST /api/v1/settings/sonarr/test", "{}", code = 500)
            seerr.serve("PUT /api/v1/settings/sonarr/3", recordWithAnimeName)
            val vm = viewModel(ServiceType.Sonarr, id = 3)
            val ready = vm.awaitReady()
            assertNull(vm.extras.first().choices)

            vm.edit { it.copy(syncEnabled = !ready.draft.syncEnabled) }
            assertTrue(vm.awaitReady().draft.valid)
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("PUT", "/api/v1/settings/sonarr/3")).jsonObject
            assertEquals("HD", sent.getValue("activeProfileName").jsonPrimitive.content)
            assertEquals("HD", sent.getValue("activeAnimeProfileName").jsonPrimitive.content)
        }

    @Test
    fun `a stale profile, folder or tag from a deleted destination is dropped by the load-time test, not just a manual one`() =
        runTest {
            val staleRecord =
                SONARR.trim().removePrefix("[").removeSuffix("]").replace(
                    "\"activeProfileId\":5,\"activeProfileName\":\"HD\",\"activeDirectory\":\"/tv\",\"tags\":[9],",
                    "\"activeProfileId\":99,\"activeProfileName\":\"Deleted\",\"activeDirectory\":\"/deleted\",\"tags\":[42],",
                )
            seerr.serve("GET /api/v1/settings/sonarr", "[$staleRecord]")
            seerr.serve("PUT /api/v1/settings/sonarr/3", staleRecord)
            val vm = viewModel(ServiceType.Sonarr, id = 3)
            val ready = vm.awaitReady()
            assertEquals(5, ready.draft.profileId)
            assertEquals("/tv", ready.draft.rootFolder)
            assertTrue(ready.draft.tagIds.isEmpty())
            assertFalse(ready.dirty)

            vm.edit { it.copy(syncEnabled = !ready.draft.syncEnabled) }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())
            val sent = Json.parseToJsonElement(seerr.body("PUT", "/api/v1/settings/sonarr/3")).jsonObject
            assertEquals("5", sent.getValue("activeProfileId").jsonPrimitive.content)
            assertEquals("/tv", sent.getValue("activeDirectory").jsonPrimitive.content)
        }

    @Test
    fun `deleting an instance removes it and reports the page done`() =
        runTest {
            seerr.serve("DELETE /api/v1/settings/sonarr/3")
            val vm = viewModel(ServiceType.Sonarr, id = 3)
            vm.awaitReady()
            vm.delete()
            assertTrue(vm.deleted.first { it })
            assertEquals(1, seerr.count("DELETE", "/api/v1/settings/sonarr/3"))
        }
}
