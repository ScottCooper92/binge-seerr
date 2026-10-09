package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.RecordingAnalytics
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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

private const val RADARR =
    """[{"id":1,"name":"Movies","hostname":"radarr.local","port":7878,"apiKey":"r-key","activeProfileId":4,"activeDirectory":"/movies"},
        {"id":2,"name":"Movies 4K","hostname":"radarr4k.local","port":7879,"apiKey":"r4-key","activeProfileId":6,"activeDirectory":"/movies-4k","is4k":true}]"""

private const val RULES =
    """[{"id":11,"radarrServiceId":2,"sonarrServiceId":null,"users":"3,5","genre":"28,12","language":"en|fr","keywords":null,
         "profileId":6,"rootFolder":"/movies-4k","tags":"1,2"}]"""

private const val TEST_RESULT =
    """{"profiles":[{"id":4,"name":"HD-1080p"},{"id":6,"name":"Ultra-HD"}],"rootFolders":[{"id":1,"path":"/movies"},{"id":2,"path":"/movies-4k"}],
        "tags":[{"id":1,"label":"binge"},{"id":2,"label":"kids"}]}"""

/** A Sonarr instance, and its choices, which differ from every Radarr one's (#1021). */
private const val SONARR =
    """[{"id":7,"name":"Shows","hostname":"sonarr.local","port":8989,"apiKey":"s-key","activeProfileId":9,"activeDirectory":"/tv"}]"""

private const val SONARR_TEST_RESULT =
    """{"profiles":[{"id":9,"name":"Any"}],"rootFolders":[{"id":5,"path":"/tv"}],"tags":[]}"""

/** Long enough for a second pick to land while the first instance's test is still out. */
private const val SLOW_TEST_MILLIS = 400L

private const val USERS = """{"results":[{"id":3,"displayName":"Ann"},{"id":5,"username":"bob"}]}"""

class OverrideRuleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()
    private val analytics = RecordingAnalytics()

    @Before
    fun setUp() {
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
        val vm = OverrideRuleViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, id, analytics)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun OverrideRuleViewModel.awaitReady(
        where: (ExtrasEditorUiState.Ready<OverrideRuleForm, OverrideRuleExtras>) -> Boolean = { true },
    ): ExtrasEditorUiState.Ready<OverrideRuleForm, OverrideRuleExtras> =
        uiState.first { it is ExtrasEditorUiState.Ready && !it.saving && where(it) }
            as ExtrasEditorUiState.Ready<OverrideRuleForm, OverrideRuleExtras>

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

            val extras = vm.awaitReady { it.extras.choices != null && !it.extras.loadingChoices }.extras
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
            assertNull(ready.extras.choices)

            vm.selectInstance(vm.awaitReady { it.extras.instances.isNotEmpty() }.extras.instances[0])
            val draft = vm.awaitReady().draft
            assertEquals(1, draft.serviceId)
            assertNull(draft.profileId)
            assertEquals(
                listOf("HD-1080p", "Ultra-HD"),
                vm
                    .awaitReady { it.extras.choices != null }
                    .extras.choices
                    ?.profiles
                    ?.map { it.label },
            )
        }

    @Test
    fun `a rule on an instance is not saved until it has a condition and an override`() =
        runTest {
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectInstance(vm.awaitReady { it.extras.instances.isNotEmpty() }.extras.instances[0])
            vm.awaitReady { it.extras.choices != null }
            vm.save()
            vm.toggleUser(3)
            vm.save()
            assertFalse(vm.awaitReady().draft.valid)
            assertEquals(0, seerr.count("POST", "/api/v1/overrideRule"))

            vm.toggleTag(2)
            assertTrue(vm.awaitReady().draft.valid)
        }

    @Test
    fun `saving encodes users and tags with commas, languages with pipes, and leaves empty conditions out`() =
        runTest {
            seerr.serve("POST /api/v1/overrideRule", """{"id":12,"radarrServiceId":1,"users":"3","language":"en|de","tags":"2"}""")
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectInstance(vm.awaitReady { it.extras.instances.isNotEmpty() }.extras.instances[0])
            vm.awaitReady { it.extras.choices != null }
            vm.toggleUser(3)
            vm.toggleTag(2)
            vm.edit { it.copy(languages = "en, de", genres = " ", keywords = "abc") }
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/overrideRule")).jsonObject
            assertEquals("1", sent.getValue("radarrServiceId").jsonPrimitive.content)
            assertNull(sent["sonarrServiceId"])
            assertEquals("3", sent.getValue("users").jsonPrimitive.content)
            assertEquals("en|de", sent.getValue("language").jsonPrimitive.content)
            assertEquals("2", sent.getValue("tags").jsonPrimitive.content)
            assertNull(sent["genre"])
            assertNull(sent["keywords"])
            assertEquals(12, vm.awaitReady().saved.id)
            assertEquals(listOf("override_rule_changed" to mapOf("action" to "created")), analytics.events)
        }

    /** A slow answer for the instance picked first must not land under the one picked after it (#1021). */
    @Test
    fun `picking a second instance while the first one's choices load keeps the second one's choices`() =
        runTest {
            seerr.serve("GET /api/v1/settings/sonarr", SONARR)
            seerr.serve("POST /api/v1/settings/sonarr/test", SONARR_TEST_RESULT)
            seerr.serveFrom("POST /api/v1/settings/radarr/test", delayMillis = SLOW_TEST_MILLIS) { TEST_RESULT }
            val vm = viewModel(null)
            val instances = vm.awaitReady { it.extras.instances.isNotEmpty() }.extras.instances

            vm.selectInstance(instances.first { it.type == ServiceType.Radarr })
            seerr.awaitCount("POST", "/api/v1/settings/radarr/test", moreThan = 0)
            vm.selectInstance(instances.first { it.type == ServiceType.Sonarr })
            vm.awaitReady { it.extras.choices != null }
            // Real time, since the slow answer comes back on OkHttp's own threads.
            withContext(Dispatchers.IO) { delay(SLOW_TEST_MILLIS * 2) }

            val ready = vm.awaitReady()
            assertEquals(7, ready.draft.serviceId)
            assertEquals(listOf("/tv"), ready.extras.choices?.rootFolders)
        }

    @Test
    fun `deleting a rule removes it and reports the page done`() =
        runTest {
            seerr.serve("DELETE /api/v1/overrideRule/11")
            val vm = viewModel(id = 11)
            vm.awaitReady()
            val deleted = awaitEvent(vm.events)
            vm.delete()
            assertEquals(EditorEvent.Deleted, deleted.await())
            assertEquals(listOf("override_rule_changed" to mapOf("action" to "deleted")), analytics.events)
        }

    @Test
    fun `deleting a just-created rule uses the id the server just assigned, not the still-null constructor id`() =
        runTest {
            seerr.serve("POST /api/v1/overrideRule", """{"id":12,"radarrServiceId":1}""")
            seerr.serve("DELETE /api/v1/overrideRule/12")
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectInstance(vm.awaitReady { it.extras.instances.isNotEmpty() }.extras.instances[0])
            vm.awaitReady { it.extras.choices != null }
            vm.toggleUser(3)
            vm.toggleTag(2)
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())
            assertEquals(12, vm.awaitReady().saved.id)

            val deleted = awaitEvent(vm.events)
            vm.delete()
            assertEquals(EditorEvent.Deleted, deleted.await())
            assertEquals(1, seerr.count("DELETE", "/api/v1/overrideRule/12"))
        }
}
