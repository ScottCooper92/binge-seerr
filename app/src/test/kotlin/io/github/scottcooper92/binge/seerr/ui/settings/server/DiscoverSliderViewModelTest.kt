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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SLIDERS = """[{"id":3,"type":13,"title":"Heist films","isBuiltIn":false,"enabled":true,"data":"10051,9882"}]"""

class DiscoverSliderViewModelTest {
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
        seerr.serve(
            "POST /api/v1/settings/discover/add",
            """{"id":5,"type":18,"title":"HBO","isBuiltIn":false,"enabled":true,"data":"49"}""",
        )
        seerr.serve(
            "PUT /api/v1/settings/discover/3",
            """{"id":3,"type":13,"title":"Heists","isBuiltIn":false,"enabled":true,"data":"10051"}""",
        )
        seerr.serve("DELETE /api/v1/settings/discover/3", SLIDERS.trim().removePrefix("[").removeSuffix("]"))
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(id: Int?): DiscoverSliderViewModel {
        val vm = DiscoverSliderViewModel(seerr.connection(this), id)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun DiscoverSliderViewModel.awaitReady(): EditorUiState.Ready<SliderForm> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<SliderForm>

    @Test
    fun `a new slider needs a title and data, and posts its kind's number with them`() =
        runTest {
            val vm = viewModel(id = null)
            assertFalse(vm.awaitReady().draft.valid)
            vm.edit { it.copy(type = SliderType.Network, title = " HBO ", data = "49") }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/discover/add")).jsonObject
            assertEquals("18", sent.getValue("type").jsonPrimitive.content)
            assertEquals("HBO", sent.getValue("title").jsonPrimitive.content)
            assertEquals("49", sent.getValue("data").jsonPrimitive.content)
            assertEquals(5, vm.awaitReady().saved.id)
        }

    @Test
    fun `an existing slider is read from the list and put to its id`() =
        runTest {
            val vm = viewModel(id = 3)
            val draft = vm.awaitReady().draft
            assertEquals(SliderType.MovieKeyword, draft.type)
            assertEquals("10051,9882", draft.data)

            vm.edit { it.copy(title = "Heists", data = "10051") }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())
            assertEquals(
                "10051",
                Json
                    .parseToJsonElement(seerr.body("PUT", "/api/v1/settings/discover/3"))
                    .jsonObject
                    .getValue("data")
                    .jsonPrimitive.content,
            )
            assertEquals("Heists", vm.awaitReady().saved.title)
        }

    @Test
    fun `a custom slider of a kind this app does not know fails to load rather than being coerced`() =
        runTest {
            seerr.serve(
                "GET /api/v1/settings/discover",
                """[{"id":6,"type":99,"title":"Newer kind","isBuiltIn":false,"enabled":true,"data":"1"}]""",
            )
            val vm = viewModel(id = 6)
            val state = vm.uiState.first { it !is EditorUiState.Loading }
            assertTrue(state is EditorUiState.Error)
        }

    @Test
    fun `deleting a slider removes it and reports the page done`() =
        runTest {
            val vm = viewModel(id = 3)
            vm.awaitReady()
            vm.delete()
            assertTrue(vm.deleted.first { it })
            assertEquals(1, seerr.count("DELETE", "/api/v1/settings/discover/3"))
        }
}
