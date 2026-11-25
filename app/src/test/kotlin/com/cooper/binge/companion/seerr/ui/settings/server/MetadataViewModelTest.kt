package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.PageEvent
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import com.cooper.homelab.core.presentation.editor.EditorState
import com.cooper.homelab.core.presentation.editor.SAVE_AS_MADE_DELAY_MILLIS
import com.cooper.homelab.core.presentation.editor.WriteStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

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
        seerr.serve("POST /api/v1/settings/metadatas/test", """{"success":true,"tests":{"tmdb":"not tested","tvdb":"ok"}}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): MetadataViewModel {
        val vm = MetadataViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, backgroundScope)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    private suspend fun MetadataViewModel.awaitReady(): EditorState.Ready<MetadataForm, MetadataExtras> =
        state.first {
            it is EditorState.Ready<*, *> && it.saveStatus != WriteStatus.Saving
        } as EditorState.Ready<MetadataForm, MetadataExtras>

    @Test
    fun `the providers are read and put back by their codes`() =
        runTest {
            val vm = viewModel()
            val draft = vm.awaitReady().draft
            assertEquals(MetadataProvider.Tvdb, draft.tv)
            assertEquals(MetadataProvider.Tmdb, draft.anime)

            vm.edit { it.copy(anime = MetadataProvider.Tvdb) }
            // Metadata saves as it changes: the pick goes out once the delay has passed.
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.state.first { it is EditorState.Ready<*, *> && !it.dirty }
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
            val notice = awaitEvent(vm.pageEvents)
            vm.test()
            assertEquals(PageEvent.Notice(AdminR.string.server_settings_metadata_tested), notice.await())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/metadatas/test")).jsonObject
            assertEquals("true", sent.getValue("tvdb").jsonPrimitive.content)
            assertEquals("false", sent.getValue("tmdb").jsonPrimitive.content)

            seerr.serve("POST /api/v1/settings/metadatas/test", """{"message":"TVDB unreachable"}""", code = 500)
            val failed = awaitEvent(vm.pageEvents)
            vm.test()
            assertTrue(failed.await() is PageEvent.Failed)
        }

    @Test
    fun `each provider's result is kept, from a pass and from a failure's body alike`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()
            val passed = awaitEvent(vm.pageEvents)
            vm.test()
            passed.await()
            assertEquals(ProviderCheck.Operational, vm.awaitReady().extras.tvdb)
            assertEquals(ProviderCheck.NotTested, vm.awaitReady().extras.tmdb)

            seerr.serve(
                "POST /api/v1/settings/metadatas/test",
                """{"success":false,"tests":{"tmdb":"ok","tvdb":"failed"}}""",
                code = 500,
            )
            val failed = awaitEvent(vm.pageEvents)
            vm.test()
            failed.await()
            assertEquals(ProviderCheck.Failed, vm.awaitReady().extras.tvdb)
            assertEquals(ProviderCheck.Operational, vm.awaitReady().extras.tmdb)
        }
}
