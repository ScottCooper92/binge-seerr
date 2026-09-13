package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.R
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
import kotlinx.serialization.json.jsonArray
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

private const val SLIDERS =
    """[{"id":1,"type":1,"title":null,"isBuiltIn":true,"enabled":true,"data":null},
        {"id":2,"type":4,"title":null,"isBuiltIn":true,"enabled":false,"data":null},
        {"id":3,"type":13,"title":"Heist films","isBuiltIn":false,"enabled":true,"data":"10051,9882"},
        {"id":4,"type":99,"title":"Newer kind","isBuiltIn":true,"enabled":true,"data":null}]"""

class DiscoverSlidersViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/settings/discover", SLIDERS)
        seerr.serve("POST /api/v1/settings/discover", SLIDERS)
        seerr.serve("GET /api/v1/settings/discover/reset", "", code = 204)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): DiscoverSlidersViewModel {
        val vm = DiscoverSlidersViewModel(seerr.connection(this))
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.reload()
        return vm
    }

    private suspend fun DiscoverSlidersViewModel.awaitReady(): EditorUiState.Ready<List<DiscoverSlider>> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<List<DiscoverSlider>>

    @Test
    fun `the list is read in the server's order, a kind this app does not know kept by its number`() =
        runTest {
            val sliders = viewModel().awaitReady().draft
            assertEquals(listOf(1, 2, 3, 4), sliders.map { it.id })
            assertEquals(SliderType.RecentlyAdded, sliders[0].type)
            assertFalse(sliders[1].enabled)
            assertEquals("Heist films", sliders[2].title)
            assertNull(sliders[3].type)
            assertEquals(99, sliders[3].typeCode)
        }

    @Test
    fun `moving and switching sliders saves the whole list in the new order, every field carried`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()
            vm.move(3, up = true)
            vm.move(3, up = true)
            vm.move(1, up = false)
            vm.toggle(2)
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/discover")).jsonArray.map { it.jsonObject }
            assertEquals(
                listOf(3, 2, 1, 4),
                sent.map {
                    it
                        .getValue("id")
                        .jsonPrimitive.content
                        .toInt()
                },
            )
            assertEquals("true", sent[1].getValue("enabled").jsonPrimitive.content)
            assertEquals("10051,9882", sent[0].getValue("data").jsonPrimitive.content)
            assertEquals("99", sent[3].getValue("type").jsonPrimitive.content)
            assertEquals("true", sent[3].getValue("isBuiltIn").jsonPrimitive.content)
        }

    @Test
    fun `reset asks the server for its defaults and reads the list again`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()
            vm.move(3, up = true)
            vm.reset()
            assertEquals(EditorEvent.Notice(R.string.server_settings_sliders_reset_done), vm.events.first())
            assertEquals(1, seerr.count("GET", "/api/v1/settings/discover/reset"))
            val ready = vm.uiState.first { it is EditorUiState.Ready && !it.dirty } as EditorUiState.Ready<List<DiscoverSlider>>
            assertEquals(listOf(1, 2, 3, 4), ready.draft.map { it.id })
            assertEquals(2, seerr.count("GET", "/api/v1/settings/discover"))
        }
}
