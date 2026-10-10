package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.SAVE_AS_MADE_DELAY_MILLIS
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
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
        val vm = DiscoverSlidersViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, backgroundScope)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.reload()
        return vm
    }

    private suspend fun DiscoverSlidersViewModel.awaitReady(): ExtrasEditorUiState.Ready<List<DiscoverSlider>, SliderNames> =
        uiState.first { it is ExtrasEditorUiState.Ready && !it.saving } as ExtrasEditorUiState.Ready<List<DiscoverSlider>, SliderNames>

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
    fun `moving and switching sliders writes the whole list once, in the new order, every field carried`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()
            vm.move(from = 2, to = 1)
            vm.move(from = 1, to = 0)
            vm.move(from = 1, to = 2)
            vm.toggle(2)
            // The list saves as it changes: the moves and the switch go out together, once they stop.
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.uiState.first { it is ExtrasEditorUiState.Ready && !it.dirty }
            assertEquals(1, seerr.count("POST", "/api/v1/settings/discover"))

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
            vm.move(from = 2, to = 1)
            val notice = awaitEvent(vm.events)
            vm.reset()
            assertEquals(EditorEvent.Notice(R.string.server_settings_sliders_reset_done), notice.await())
            assertEquals(1, seerr.count("GET", "/api/v1/settings/discover/reset"))
            val ready =
                vm.uiState.first {
                    it is ExtrasEditorUiState.Ready && !it.dirty
                } as ExtrasEditorUiState.Ready<List<DiscoverSlider>, SliderNames>
            assertEquals(listOf(1, 2, 3, 4), ready.draft.map { it.id })
            assertEquals(2, seerr.count("GET", "/api/v1/settings/discover"))
        }

    /** A move still waiting out its save delay when Reset is tapped is dropped, not written over the reset (#1019). */
    @Test
    fun `a change still waiting to save when reset is tapped is never written`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()
            vm.toggle(2)
            val notice = awaitEvent(vm.events)

            vm.reset()
            assertEquals(EditorEvent.Notice(R.string.server_settings_sliders_reset_done), notice.await())
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS * 2)
            runCurrent()

            // A write goes out on OkHttp's threads, so a count taken at once can miss one on its way (#986).
            assertFalse(seerr.awaitCountHoldingTime("POST", "/api/v1/settings/discover", moreThan = 0))
            assertFalse(
                vm
                    .awaitReady()
                    .draft
                    .single { it.id == 2 }
                    .enabled,
            )
        }

    @Test
    fun `what the custom sliders hold is named for their captions, an id left as a number where the server cannot say`() =
        runTest {
            seerr.serve(
                "GET /api/v1/settings/discover",
                """[{"id":4,"type":13,"title":"Kaiju","isBuiltIn":false,"enabled":true,"data":"210024,4344"},
                    {"id":5,"type":15,"title":"Sci-fi","isBuiltIn":false,"enabled":true,"data":"878"},
                    {"id":6,"type":17,"title":"Marvel","isBuiltIn":false,"enabled":true,"data":"420"},
                    {"id":7,"type":18,"title":"HBO","isBuiltIn":false,"enabled":true,"data":"49"},
                    {"id":8,"type":20,"title":"Streaming","isBuiltIn":false,"enabled":true,"data":"GB,8|337"}]""",
            )
            seerr.serve("GET /api/v1/keyword/210024", """{"id":210024,"name":"kaiju"}""")
            seerr.serve("GET /api/v1/keyword/4344", "{}", code = 404)
            seerr.serve("GET /api/v1/genres/movie", """[{"id":878,"name":"Science Fiction"}]""")
            seerr.serve("GET /api/v1/studio/420", """{"id":420,"name":"Marvel Studios"}""")
            seerr.serve("GET /api/v1/network/49", """{"id":49,"name":"HBO"}""")
            seerr.serve("GET /api/v1/watchproviders/movies", """[{"id":8,"name":"Netflix"},{"id":337,"name":"Disney Plus"}]""")
            val vm = viewModel()
            val ready =
                vm.uiState.first {
                    it is ExtrasEditorUiState.Ready &&
                        it.extras.keywords.isNotEmpty() &&
                        it.extras.providers.isNotEmpty() &&
                        it.extras.studios.isNotEmpty() &&
                        it.extras.networks.isNotEmpty() &&
                        it.extras.genres.isNotEmpty()
                } as ExtrasEditorUiState.Ready<List<DiscoverSlider>, SliderNames>
            val labels = ready.draft.filter { !it.builtIn }.associate { it.id to it.dataLabel(ready.extras) { code -> code } }
            assertEquals("kaiju, 4344", labels[4])
            assertEquals("Science Fiction", labels[5])
            assertEquals("Marvel Studios", labels[6])
            assertEquals("HBO", labels[7])
            assertEquals("GB · Netflix, Disney Plus", labels[8])
        }
}
