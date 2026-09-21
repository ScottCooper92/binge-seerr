package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.R
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MetadataViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/settings/metadatas", """{"tv":"tvdb","anime":"tmdb"}""")
        seerr.serve(
            "PUT /api/v1/settings/metadatas",
            """{"success":true,"tv":"tvdb","anime":"tvdb","tests":{"tvdb":"ok","tmdb":"not tested"}}""",
        )
        seerr.serve("POST /api/v1/settings/metadatas/test", """{"message":"Successfully connected to TVDB"}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): MetadataViewModel {
        val vm = MetadataViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun MetadataViewModel.awaitReady(): ExtrasEditorUiState.Ready<MetadataForm, MetadataExtras> =
        uiState.first { it is ExtrasEditorUiState.Ready && !it.saving } as ExtrasEditorUiState.Ready<MetadataForm, MetadataExtras>

    @Test
    fun `the providers are read and put back by their codes`() =
        runTest {
            val vm = viewModel()
            val draft = vm.awaitReady().draft
            assertEquals(MetadataProvider.Tvdb, draft.tv)
            assertEquals(MetadataProvider.Tmdb, draft.anime)

            vm.edit { it.copy(anime = MetadataProvider.Tvdb) }
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())
            val sent = Json.parseToJsonElement(seerr.body("PUT", "/api/v1/settings/metadatas")).jsonObject
            assertEquals("tvdb", sent.getValue("anime").jsonPrimitive.content)
            assertEquals("tvdb", sent.getValue("tv").jsonPrimitive.content)
            assertEquals(MetadataProvider.Tvdb, vm.awaitReady().saved.anime)
        }

    @Test
    fun `a test reaches only the providers the draft would use, and a failure is reported`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()
            vm.edit { it.copy(anime = MetadataProvider.Tvdb) }
            val notice = awaitEvent(vm.events)
            vm.test()
            assertEquals(EditorEvent.Notice(R.string.server_settings_metadata_tested), notice.await())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/metadatas/test")).jsonObject
            assertEquals("true", sent.getValue("tvdb").jsonPrimitive.content)
            assertEquals("false", sent.getValue("tmdb").jsonPrimitive.content)

            seerr.serve("POST /api/v1/settings/metadatas/test", """{"message":"TVDB unreachable"}""", code = 500)
            val failed = awaitEvent(vm.events)
            vm.test()
            assertTrue(failed.await() is EditorEvent.Failed)
        }
}
