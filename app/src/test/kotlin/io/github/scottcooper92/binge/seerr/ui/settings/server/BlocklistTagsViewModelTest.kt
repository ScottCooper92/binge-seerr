package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.SAVE_AS_MADE_DELAY_MILLIS
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

private const val MAIN =
    """{"applicationTitle":"Home","blocklistRegion":"","blocklistLanguage":"ja",
       "blocklistedTags":"9951","blocklistedTagsLimit":50}"""

/** Jellyseerr 2.6 to 2.x: the old names, and none of Seerr 3.0's. */
private const val OLD_NAMES_MAIN =
    """{"applicationTitle":"Home","hideBlacklisted":false,"blacklistedTags":"9951","blacklistedTagsLimit":50}"""

/** The tags page saves as it changes (#930): a burst of changes is one request, carrying the tags alone. */
class BlocklistTagsViewModelTest {
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
        seerr.serve("GET /api/v1/settings/main", MAIN)
        seerr.serve("POST /api/v1/settings/main", MAIN)
        seerr.serve("GET /api/v1/keyword/9951", """{"id":9951,"name":"kaiju"}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): BlocklistTagsViewModel {
        val vm = BlocklistTagsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, backgroundScope)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun BlocklistTagsViewModel.awaitReady(
        where: (BlocklistTagsUiState.Ready) -> Boolean = {
            true
        },
    ): BlocklistTagsUiState.Ready = uiState.first { it is BlocklistTagsUiState.Ready && where(it) } as BlocklistTagsUiState.Ready

    private fun sent(): Map<String, String> =
        Json
            .parseToJsonElement(seerr.body("POST", "/api/v1/settings/main"))
            .jsonObject
            .mapValues { it.value.jsonPrimitive.content }

    @Test
    fun `the tags are read and named`() =
        runTest {
            val ready = viewModel().awaitReady { it.names.isNotEmpty() }
            assertEquals(listOf(9951), ready.tags)
            assertEquals(mapOf(9951 to "kaiju"), ready.names)
        }

    @Test
    fun `a burst of changes saves once, sending the tags and nothing else`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()

            vm.toggle(4344)
            vm.toggle(210024)
            vm.toggle(9951)
            assertEquals(listOf(4344, 210024), vm.awaitReady().tags)
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.uiState.first { seerr.count("POST", "/api/v1/settings/main") == 1 }

            assertEquals(mapOf("blocklistedTags" to "4344,210024"), sent())
        }

    @Test
    fun `leaving the page before the delay is up still sends the change, once`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()

            vm.toggle(4344)
            vm.awaitReady { it.tags == listOf(9951, 4344) }
            viewModels.clear()
            seerr.awaitCount("POST", "/api/v1/settings/main", moreThan = 0)
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS * 2)

            assertEquals(1, seerr.count("POST", "/api/v1/settings/main"))
            assertEquals(mapOf("blocklistedTags" to "9951,4344"), sent())
        }

    @Test
    fun `a server on the old names gets the tags under blacklistedTags`() =
        runTest {
            seerr.serve("GET /api/v1/settings/main", OLD_NAMES_MAIN)
            seerr.serve("POST /api/v1/settings/main", OLD_NAMES_MAIN)
            val vm = viewModel()
            vm.awaitReady()

            vm.toggle(4344)
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.uiState.first { seerr.count("POST", "/api/v1/settings/main") == 1 }

            assertEquals(mapOf("blacklistedTags" to "9951,4344"), sent())
        }

    @Test
    fun `a failed save keeps the tags, and the next change sends them all in one request`() =
        runTest {
            seerr.serve("POST /api/v1/settings/main", """{"message":"nope"}""", code = 500)
            val vm = viewModel()
            vm.awaitReady()

            vm.toggle(4344)
            val failed = vm.awaitReady { it.saveFailed }
            assertEquals("the change stays on screen, unsaved", listOf(9951, 4344), failed.tags)

            seerr.serve("POST /api/v1/settings/main", MAIN)
            vm.toggle(210024)
            val saved = vm.awaitReady { !it.saveFailed }
            seerr.awaitCount("POST", "/api/v1/settings/main", moreThan = 1)
            assertEquals(listOf(9951, 4344, 210024), saved.tags)
            assertEquals(mapOf("blocklistedTags" to "9951,4344,210024"), sent())
        }

    @Test
    fun `retry sends the tags as they stand`() =
        runTest {
            seerr.serve("POST /api/v1/settings/main", """{"message":"nope"}""", code = 500)
            val vm = viewModel()
            vm.awaitReady()
            vm.toggle(4344)
            vm.awaitReady { it.saveFailed }

            seerr.serve("POST /api/v1/settings/main", MAIN)
            vm.retry()
            vm.awaitReady { !it.saveFailed }
            seerr.awaitCount("POST", "/api/v1/settings/main", moreThan = 1)
            assertEquals(mapOf("blocklistedTags" to "9951,4344"), sent())
            assertEquals(2, seerr.count("POST", "/api/v1/settings/main"))
        }

    @Test
    fun `a retry that fails again reports the failure again`() =
        runTest {
            seerr.serve("POST /api/v1/settings/main", """{"message":"nope"}""", code = 500)
            val vm = viewModel()
            vm.awaitReady()
            vm.toggle(4344)
            vm.awaitReady { it.saveFailed }
            val seen = mutableListOf<Boolean>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.uiState
                    .mapNotNull { (it as? BlocklistTagsUiState.Ready)?.saveFailed }
                    .distinctUntilChanged()
                    .collect { seen += it }
            }

            vm.retry()
            vm.uiState.first { seerr.count("POST", "/api/v1/settings/main") == 2 }
            vm.awaitReady { it.saveFailed }

            assertEquals("cleared when the retry starts, set again when it fails", listOf(true, false, true), seen)
        }

    @Test
    fun `a search lists what tmdb matches, names it, and blank clears it`() =
        runTest {
            seerr.serve("GET /api/v1/search/keyword", """{"results":[{"id":4344,"name":"musical"},{"id":5,"name":null}]}""")
            val vm = viewModel()
            vm.awaitReady()

            vm.search("mus")
            val found = vm.awaitReady { it.search.results != null }
            assertEquals(listOf(Keyword(4344, "musical")), found.search.results)
            assertEquals("musical", found.names[4344])

            vm.search("")
            assertEquals(null, vm.awaitReady { it.search.results == null }.search.results)
        }

    @Test
    fun `a search cancelled by a newer query does not report a failure`() =
        runTest {
            seerr.serveFrom("GET /api/v1/search/keyword", delayMillis = 200) { """{"results":[{"id":4344,"name":"musical"}]}""" }
            val vm = viewModel()
            vm.awaitReady()
            val failures = mutableListOf<Boolean>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.uiState.collect { state -> if (state is BlocklistTagsUiState.Ready) failures += state.search.failed }
            }

            vm.search("mu")
            advanceTimeBy(KEYWORD_SEARCH_DEBOUNCE_MILLIS + 1)
            vm.awaitReady { it.search.searching }
            vm.search("mus")
            val found = vm.awaitReady { it.search.results != null }

            assertEquals(listOf(Keyword(4344, "musical")), found.search.results)
            assertFalse(failures.any { it })
            assertTrue(found.tags.isNotEmpty())
        }
}
