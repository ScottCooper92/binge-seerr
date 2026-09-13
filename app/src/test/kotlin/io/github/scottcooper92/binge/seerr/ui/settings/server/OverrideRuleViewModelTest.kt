package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val RADARR =
    """[{"id":1,"name":"Movies","hostname":"radarr.local","port":7878,"apiKey":"r-key","activeProfileId":4,"activeDirectory":"/movies"},
        {"id":2,"name":"Movies 4K","hostname":"radarr4k.local","port":7879,"apiKey":"r4-key","activeProfileId":6,"activeDirectory":"/movies-4k","is4k":true}]"""

private const val RULES =
    """[{"id":11,"radarrServiceId":2,"sonarrServiceId":null,"users":"3,5","genre":"28,12","language":"en|fr","keywords":null,
         "profileId":6,"rootFolder":"/movies-4k","tags":"1,2"}]"""

private const val TEST_RESULT =
    """{"profiles":[{"id":4,"name":"HD-1080p"},{"id":6,"name":"Ultra-HD"}],"rootFolders":[{"id":1,"path":"/movies"},{"id":2,"path":"/movies-4k"}],
        "tags":[{"id":1,"label":"binge"},{"id":2,"label":"kids"}]}"""

private const val USERS = """{"results":[{"id":3,"displayName":"Ann"},{"id":5,"username":"bob"}]}"""

class OverrideRuleViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/settings/radarr", RADARR)
        seerr.serve("GET /api/v1/settings/sonarr", "[]")
        seerr.serve("POST /api/v1/settings/radarr/test", TEST_RESULT)
        seerr.serve("GET /api/v1/overrideRule", RULES)
        seerr.serve("GET /api/v1/user", USERS)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(id: Int?): OverrideRuleViewModel {
        val vm = OverrideRuleViewModel(seerr.connection(this), id)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun OverrideRuleViewModel.awaitReady(): EditorUiState.Ready<OverrideRuleForm> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<OverrideRuleForm>

    @Test
    fun `an existing rule decodes its conditions and loads its instance's choices with the stored key`() =
        runTest {
            val vm = viewModel(id = 11)
            val draft = vm.awaitReady().draft
            assertEquals(ServiceType.Radarr, draft.serviceType)
            assertEquals(2, draft.serviceId)
            assertEquals(setOf(3, 5), draft.userIds)
            assertEquals("28,12", draft.genres)
            assertEquals("en, fr", draft.languages)
            assertEquals("", draft.keywords)
            assertEquals(6, draft.profileId)
            assertEquals(setOf(1, 2), draft.tagIds)

            val extras = vm.extras.first { it.choices != null && !it.loadingChoices }
            assertEquals(listOf("Movies", "Movies 4K"), extras.instances.map { it.name })
            assertEquals(listOf("Ann", "bob"), extras.users.map { it.label })
            assertEquals(
                "r4-key",
                Json
                    .parseToJsonElement(
                        seerr.body("POST", "/api/v1/settings/radarr/test"),
                    ).jsonObject
                    .getValue("apiKey")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `a new rule cannot be saved without an instance, and picking one clears the overrides`() =
        runTest {
            val vm = viewModel(id = null)
            val ready = vm.awaitReady()
            assertFalse(ready.draft.valid)
            assertNull(vm.extras.first().choices)

            vm.selectInstance(vm.extras.first { it.instances.isNotEmpty() }.instances[0])
            val draft = vm.awaitReady().draft
            assertEquals(1, draft.serviceId)
            assertNull(draft.profileId)
            assertEquals(
                listOf("HD-1080p", "Ultra-HD"),
                vm.extras
                    .first { it.choices != null }
                    .choices
                    ?.profiles
                    ?.map { it.label },
            )
        }

    @Test
    fun `saving encodes users and tags with commas, languages with pipes, and leaves empty conditions out`() =
        runTest {
            seerr.serve("POST /api/v1/overrideRule", """{"id":12,"radarrServiceId":1,"users":"3","language":"en|de","tags":"2"}""")
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectInstance(vm.extras.first { it.instances.isNotEmpty() }.instances[0])
            vm.extras.first { it.choices != null }
            vm.toggleUser(3)
            vm.toggleTag(2)
            vm.edit { it.copy(languages = "en, de", genres = " ", keywords = "abc") }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/overrideRule")).jsonObject
            assertEquals("1", sent.getValue("radarrServiceId").jsonPrimitive.content)
            assertNull(sent["sonarrServiceId"])
            assertEquals("3", sent.getValue("users").jsonPrimitive.content)
            assertEquals("en|de", sent.getValue("language").jsonPrimitive.content)
            assertEquals("2", sent.getValue("tags").jsonPrimitive.content)
            assertNull(sent["genre"])
            assertNull(sent["keywords"])
            assertEquals(12, vm.awaitReady().saved.id)
        }

    @Test
    fun `deleting a rule removes it and reports the page done`() =
        runTest {
            seerr.serve("DELETE /api/v1/overrideRule/11")
            val vm = viewModel(id = 11)
            vm.awaitReady()
            vm.delete()
            assertEquals(true, vm.deleted.first { it })
        }
}
