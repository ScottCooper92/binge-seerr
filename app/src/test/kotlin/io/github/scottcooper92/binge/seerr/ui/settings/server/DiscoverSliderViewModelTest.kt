package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
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

private const val SLIDERS = """[{"id":3,"type":13,"title":"Heist films","isBuiltIn":false,"enabled":true,"data":"10051,9882"}]"""

class DiscoverSliderViewModelTest {
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
        seerr.serve(
            "POST /api/v1/settings/discover/add",
            """{"id":5,"type":18,"title":"HBO","isBuiltIn":false,"enabled":true,"data":"49"}""",
        )
        seerr.serve(
            "PUT /api/v1/settings/discover/3",
            """{"id":3,"type":13,"title":"Heists","isBuiltIn":false,"enabled":true,"data":"10051"}""",
        )
        // The server answers a delete 204 with no body at all, which is why nothing is decoded from it.
        seerr.serve("DELETE /api/v1/settings/discover/3", body = "", code = 204)
        seerr.serve("GET /api/v1/keyword/10051", """{"id":10051,"name":"heist"}""")
        seerr.serve("GET /api/v1/keyword/9882", """{"id":9882,"name":"bank robbery"}""")
        seerr.serve("GET /api/v1/genres/movie", """[{"id":28,"name":"Action"},{"id":12,"name":"Adventure"}]""")
        seerr.serve("GET /api/v1/genres/tv", """[{"id":10759,"name":"Action & Adventure"}]""")
        seerr.serve(
            "GET /api/v1/search/company",
            """{"results":[{"id":420,"name":"Marvel Studios"},{"id":2,"name":"Walt Disney Pictures"}]}""",
        )
        seerr.serve("GET /api/v1/studio/420", """{"id":420,"name":"Marvel Studios"}""")
        seerr.serve("GET /api/v1/network/49", """{"id":49,"name":"HBO"}""")
        seerr.serve(
            "GET /api/v1/watchproviders/regions",
            """[{"iso_3166_1":"GB","english_name":"United Kingdom"},{"iso_3166_1":"US","english_name":"United States"}]""",
        )
        seerr.serve("GET /api/v1/watchproviders/movies", """[{"id":337,"name":"Disney Plus"},{"id":8,"name":"Netflix"}]""")
        seerr.serve("GET /api/v1/watchproviders/tv", """[{"id":9,"name":"Prime Video"}]""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(
        id: Int?,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): DiscoverSliderViewModel {
        val connection = seerr.connection(this)
        val vm = DiscoverSliderViewModel(connection, ServerListCatalog(connection), mainDispatcherRule.dispatcher, id, savedState)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun DiscoverSliderViewModel.awaitReady(
        where: (ExtrasEditorUiState.Ready<SliderForm, SliderExtras>) -> Boolean = { true },
    ): ExtrasEditorUiState.Ready<SliderForm, SliderExtras> =
        uiState.first { it is ExtrasEditorUiState.Ready && !it.saving && where(it) } as ExtrasEditorUiState.Ready<SliderForm, SliderExtras>

    @Test
    fun `a new slider needs a title and data, and posts its kind's number with them`() =
        runTest {
            val vm = viewModel(id = null)
            assertFalse(vm.awaitReady().draft.valid)
            vm.edit { it.copy(type = SliderType.Network, title = " HBO ", data = "49") }
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

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
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())
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
            val state = vm.uiState.first { it !is ExtrasEditorUiState.Loading }
            assertTrue(state is ExtrasEditorUiState.Error)
        }

    @Test
    fun `deleting a slider removes it and reports the page done`() =
        runTest {
            val vm = viewModel(id = 3)
            vm.awaitReady()
            val deleted = awaitEvent(vm.events)
            vm.delete()
            assertEquals(EditorEvent.Deleted, deleted.await())
            assertEquals(1, seerr.count("DELETE", "/api/v1/settings/discover/3"))
        }

    @Test
    fun `keywords are named, and picked into the data as the web client stores them`() =
        runTest {
            val vm = viewModel(id = 3)
            vm.awaitReady()
            vm.loadKeywordNames(listOf(10051, 9882))
            val named = vm.awaitReady { it.extras.keywords.names.size == 2 }
            assertEquals("heist", named.extras.keywords.names[10051])
            assertEquals("bank robbery", named.extras.keywords.names[9882])

            vm.toggleKeyword(9882)
            assertEquals("10051", vm.awaitReady().draft.data)
            vm.toggleKeyword(777)
            assertEquals("10051,777", vm.awaitReady().draft.data)
        }

    @Test
    fun `opening a saved genre slider reads the genres of its kind`() =
        runTest {
            seerr.serve(
                "GET /api/v1/settings/discover",
                """[{"id":4,"type":16,"title":"Shows","isBuiltIn":false,"enabled":true,"data":"10759"}]""",
            )
            val vm = viewModel(id = 4)
            assertEquals("10759", vm.awaitReady().draft.data)
            val genres = vm.awaitReady { it.extras.genres is GenreChoices.Ready }.extras.genres as GenreChoices.Ready
            assertEquals(listOf("Action & Adventure"), genres.genres.map { it.label })
        }

    @Test
    fun `a genre slider reads the genres of its kind, and a pick is the id alone`() =
        runTest {
            val vm = viewModel(id = null)
            vm.awaitReady()

            vm.selectType(SliderType.MovieGenre)
            val movie = vm.awaitReady { it.extras.genres is GenreChoices.Ready }.extras.genres as GenreChoices.Ready
            assertEquals(listOf("Action", "Adventure"), movie.genres.map { it.label })
            vm.selectGenre(12)
            assertEquals("12", vm.awaitReady().draft.data)

            vm.selectType(SliderType.TvGenre)
            val tv = vm.awaitReady { (it.extras.genres as? GenreChoices.Ready)?.genres?.size == 1 }.extras.genres as GenreChoices.Ready
            assertEquals("Action & Adventure", tv.genres.single().label)
            // A movie genre id means nothing against TV's list, so the pick is dropped with the kind.
            assertEquals("", vm.awaitReady().draft.data)
        }

    @Test
    fun `changing to a kind that keeps its data differently drops the data`() =
        runTest {
            val vm = viewModel(id = 3)
            assertEquals("10051,9882", vm.awaitReady().draft.data)

            // Keyword to keyword keeps the ids.
            vm.selectType(SliderType.TvKeyword)
            assertEquals("10051,9882", vm.awaitReady().draft.data)
            // Keyword ids are not a genre, a company or a search.
            vm.selectType(SliderType.MovieGenre)
            assertEquals("", vm.awaitReady().draft.data)
        }

    @Test
    fun `a server that cannot send the genres leaves the genre typed`() =
        runTest {
            seerr.serve("GET /api/v1/genres/movie", "{}", code = 500)
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectType(SliderType.MovieGenre)
            assertEquals(GenreChoices.Failed, vm.awaitReady { it.extras.genres != GenreChoices.Loading }.extras.genres)
        }

    @Test
    fun `a failed genre read is tried again when the kind is picked again, and a good one is not read twice`() =
        runTest {
            seerr.serve("GET /api/v1/genres/movie", "{}", code = 500)
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectType(SliderType.MovieGenre)
            vm.awaitReady { it.extras.genres == GenreChoices.Failed }

            seerr.serve("GET /api/v1/genres/movie", """[{"id":28,"name":"Action"}]""")
            vm.selectType(SliderType.MovieGenre)
            vm.awaitReady { it.extras.genres is GenreChoices.Ready }
            assertEquals(2, seerr.count("GET", "/api/v1/genres/movie"))

            vm.selectType(SliderType.MovieGenre)
            vm.awaitReady { it.extras.genres is GenreChoices.Ready }
            assertEquals(2, seerr.count("GET", "/api/v1/genres/movie"))
        }

    @Test
    fun `a studio is found by search, picked as its id, and named on the row`() =
        runTest {
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectType(SliderType.Studio)

            vm.searchStudios("marvel")
            val found = vm.awaitReady { it.extras.studios.results != null }.extras.studios
            assertEquals(listOf("Marvel Studios", "Walt Disney Pictures"), found.results?.map { it.name })
            assertEquals(
                "marvel",
                seerr.received
                    .last { it.url.encodedPath == "/api/v1/search/company" }
                    .url
                    .queryParameter("query"),
            )

            vm.selectStudio(Company(420, "Marvel Studios"))
            val picked = vm.awaitReady { it.draft.data == "420" }
            assertEquals("Marvel Studios", picked.extras.studios.names[420])
        }

    @Test
    fun `a saved studio and a typed network are named by their ids`() =
        runTest {
            seerr.serve(
                "GET /api/v1/settings/discover",
                """[{"id":8,"type":17,"title":"Marvel","isBuiltIn":false,"enabled":true,"data":"420"}]""",
            )
            val vm = viewModel(id = 8)
            assertEquals(
                "Marvel Studios",
                vm
                    .awaitReady {
                        it.extras.studios.names
                            .isNotEmpty()
                    }.extras.studios.names[420],
            )

            vm.nameNetwork(49)
            assertEquals("HBO", vm.awaitReady { it.extras.networkNames.isNotEmpty() }.extras.networkNames[49])
        }

    @Test
    fun `a streaming slider picks a region, then that region's providers for its kind, saved as the web client stores them`() =
        runTest {
            seerr.serve(
                "POST /api/v1/settings/discover/add",
                """{"id":9,"type":20,"title":"Streaming","isBuiltIn":false,"enabled":true,"data":"GB,337|8"}""",
            )
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectType(SliderType.MovieStreamingServices)
            vm.edit { it.copy(title = "Streaming") }
            assertFalse(vm.awaitReady().draft.valid)

            vm.loadRegions()
            val regions = vm.awaitReady { it.extras.regions is ListChoices.Ready }.extras.regions as ListChoices.Ready
            assertEquals(listOf("GB", "US"), regions.entries.map { it.code })

            vm.selectRegion("GB")
            val providers = vm.awaitReady { it.extras.providers is ProviderChoices.Ready }.extras.providers as ProviderChoices.Ready
            assertEquals(listOf("Disney Plus", "Netflix"), providers.providers.map { it.label })
            assertEquals(
                "GB",
                seerr.received
                    .last { it.url.encodedPath == "/api/v1/watchproviders/movies" }
                    .url
                    .queryParameter("watchRegion"),
            )
            // A region alone is not enough to query.
            assertFalse(vm.awaitReady().draft.valid)

            vm.toggleProvider(337)
            vm.toggleProvider(8)
            assertEquals("GB,337|8", vm.awaitReady().draft.data)
            assertTrue(vm.awaitReady().draft.valid)

            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/discover/add")).jsonObject
            assertEquals("20", sent.getValue("type").jsonPrimitive.content)
            assertEquals("GB,337|8", sent.getValue("data").jsonPrimitive.content)
        }

    @Test
    fun `picking the streaming kind again leaves its providers alone`() =
        runTest {
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectType(SliderType.MovieStreamingServices)
            vm.selectRegion("GB")
            vm.awaitReady { it.extras.providers is ProviderChoices.Ready }
            assertEquals(1, seerr.count("GET", "/api/v1/watchproviders/movies"))

            vm.selectType(SliderType.MovieStreamingServices)
            assertTrue(vm.awaitReady().extras.providers is ProviderChoices.Ready)
            assertEquals(1, seerr.count("GET", "/api/v1/watchproviders/movies"))
        }

    @Test
    fun `a new region drops the providers picked for the last, and a TV kind reads TV's list`() =
        runTest {
            val vm = viewModel(id = null)
            vm.awaitReady()
            vm.selectType(SliderType.MovieStreamingServices)
            vm.selectRegion("GB")
            vm.awaitReady { it.extras.providers is ProviderChoices.Ready }
            vm.toggleProvider(337)
            assertEquals("GB,337", vm.awaitReady().draft.data)

            vm.selectRegion("US")
            assertEquals("US,", vm.awaitReady().draft.data)

            // Movie providers are not TV's, so the kind change drops the pick, and TV's list is read.
            vm.selectType(SliderType.TvStreamingServices)
            val changed = vm.awaitReady()
            assertEquals("", changed.draft.data)
            assertEquals(ProviderChoices.Idle, changed.extras.providers)
            vm.selectRegion("GB")
            val tv =
                vm.awaitReady {
                    (it.extras.providers as? ProviderChoices.Ready)?.providers?.map { c -> c.label } ==
                        listOf("Prime Video")
                }
            assertEquals(listOf("Prime Video"), (tv.extras.providers as ProviderChoices.Ready).providers.map { it.label })
        }

    @Test
    fun `the streaming data is read and written as a region and ids`() {
        assertEquals(StreamingPick("GB", listOf(8, 337)), "GB,8|337".toStreamingPick())
        assertEquals(StreamingPick("US", emptyList()), "US".toStreamingPick())
        assertEquals(StreamingPick("", emptyList()), "".toStreamingPick())
        assertEquals(StreamingPick("GB", listOf(8)), "GB,8|x".toStreamingPick())
        assertEquals("GB,8|337", StreamingPick("GB", listOf(8, 337)).encode())
        assertEquals("", StreamingPick().encode())
        assertEquals(listOf(337), StreamingPick("GB", listOf(8, 337)).withProviderToggled(8).providerIds)
    }

    @Test
    fun `an unsaved draft survives the process being killed, and its keywords are named again`() =
        runTest {
            val savedState = SavedStateHandle()
            val vm = viewModel(id = null, savedState = savedState)
            vm.awaitReady()
            vm.edit { it.copy(title = "Heists", data = "10051") }
            vm.awaitReady()

            val back = viewModel(id = null, savedState = savedState.afterProcessDeath())
            val draft = back.awaitReady().draft

            assertEquals("Heists", draft.title)
            assertEquals("10051", draft.data)
            assertEquals(
                "heist",
                back
                    .awaitReady {
                        it.extras.keywords.names
                            .isNotEmpty()
                    }.extras.keywords.names[10051],
            )
        }
}
