package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.afterProcessDeath
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TautulliViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN, settings = """{"mediaServerType":1}""")
        seerr.serve("GET /api/v1/settings/tautulli", "{}")
        seerr.serve("POST /api/v1/settings/tautulli", """{"hostname":"tautulli.local","port":8181,"useSsl":false,"apiKey":"t-key"}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()): TautulliViewModel {
        val vm = TautulliViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, savedState)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun TautulliViewModel.awaitReady(): EditorUiState.Ready<TautulliForm> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<TautulliForm>

    @Test
    fun `an unconfigured tautulli is an empty form, and half an address and no key cannot be saved`() =
        runTest {
            val vm = viewModel()
            val draft = vm.awaitReady().draft
            assertEquals(TautulliForm(), draft)

            vm.edit { it.copy(host = "tautulli.local", port = "8181") }
            vm.save()
            assertEquals(0, seerr.count("POST", "/api/v1/settings/tautulli"))
        }

    @Test
    fun `saving posts the whole record and adopts the answer`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(host = "tautulli.local", port = "8181", apiKey = "t-key") }
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/tautulli")).jsonObject
            assertEquals("tautulli.local", sent.getValue("hostname").jsonPrimitive.content)
            assertEquals("8181", sent.getValue("port").jsonPrimitive.content)
            assertEquals("t-key", sent.getValue("apiKey").jsonPrimitive.content)
            val ready = vm.awaitReady()
            assertEquals("tautulli.local", ready.saved.host)
            assertFalse(ready.dirty)
        }

    @Test
    fun `emptying the address and key clears tautulli`() =
        runTest {
            seerr.serve("GET /api/v1/settings/tautulli", """{"hostname":"tautulli.local","port":8181,"useSsl":false,"apiKey":"t-key"}""")
            seerr.serve("POST /api/v1/settings/tautulli", """{"hostname":"","port":0,"apiKey":""}""")
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(host = "", port = "", apiKey = "") }
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/tautulli")).jsonObject
            assertEquals("", sent.getValue("hostname").jsonPrimitive.content)
            assertEquals("", sent.getValue("apiKey").jsonPrimitive.content)
            assertEquals("0", sent.getValue("port").jsonPrimitive.content)
            val ready = vm.awaitReady()
            assertFalse(ready.draft.configured)
            assertEquals("", ready.draft.port)
        }

    @Test
    fun `an unsaved draft survives the process being killed, without the api key`() =
        runTest {
            val savedState = SavedStateHandle()
            val vm = viewModel(savedState)
            vm.awaitReady()
            vm.edit { it.copy(host = "tautulli.lan", port = "8181", apiKey = "s3cret") }
            vm.awaitReady()
            assertTrue(savedState.keys().none { savedState.get<Any?>(it).toString().contains("s3cret") })

            val back = viewModel(savedState.afterProcessDeath()).awaitReady()

            assertEquals("tautulli.lan", back.draft.host)
            assertEquals("", back.draft.apiKey)
            assertTrue(back.dirty)
        }
}
