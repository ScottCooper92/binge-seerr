package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val LINEAGE_MAIN =
    """{"apiKey":"old-key","applicationTitle":"Home","applicationUrl":"https://seerr.example","locale":"en",
       "discoverRegion":"GB","streamingRegion":"IE","originalLanguage":"","hideAvailable":true,"hideRequested":false,
       "partialRequestsEnabled":true,"enableSpecialEpisodes":true,"cacheImages":false,"youtubeUrl":"https://yt.example",
       "hideBlocklisted":false,"blocklistRegion":"","blocklistLanguage":"ja","blocklistedTags":"9951,210024",
       "blocklistedTagsLimit":50}"""

private const val OVERSEERR_MAIN =
    """{"apiKey":"old-key","applicationTitle":"Home","region":"US","originalLanguage":"en","hideAvailable":false,
       "partialRequestsEnabled":false,"cacheImages":true,"trustProxy":true,"csrfProtection":false}"""

class ServerGeneralViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()
    private lateinit var connection: SeerrConnection

    @Before
    fun setUp() {
        seerr.start()
        seerr.serve("GET /api/v1/settings/main", LINEAGE_MAIN)
        seerr.serve("POST /api/v1/settings/main", LINEAGE_MAIN)
        seerr.serve("POST /api/v1/settings/main/regenerate", """{"apiKey":"new-key"}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): ServerGeneralViewModel {
        connection = seerr.connection(this)
        val vm = ServerGeneralViewModel(connection, ServerListCatalog(connection), mainDispatcherRule.dispatcher)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun ServerGeneralViewModel.awaitReady(
        where: (ExtrasEditorUiState.Ready<ServerGeneralSettings, ServerGeneralExtras>) -> Boolean = { true },
    ): ExtrasEditorUiState.Ready<ServerGeneralSettings, ServerGeneralExtras> =
        uiState.first {
            it is ExtrasEditorUiState.Ready && !it.saving && where(it)
        } as ExtrasEditorUiState.Ready<ServerGeneralSettings, ServerGeneralExtras>

    @Test
    fun `the server's variant is kept for the display languages it offers`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            seerr.serve("GET /api/v1/settings/main", OVERSEERR_MAIN)

            assertEquals(SeerrVariant.Overseerr, viewModel().awaitReady().extras.variant)
        }

    @Test
    fun `a list is read when a picker asks, and only once`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/regions", """[{"iso_3166_1":"GB","english_name":"United Kingdom"},{"iso_3166_1":""}]""")
            val vm = viewModel()
            assertTrue(
                vm
                    .awaitReady()
                    .extras.lists
                    .isEmpty(),
            )

            vm.loadList(ServerList.DiscoverRegions)
            val ready = vm.awaitReady { it.extras.lists[ServerList.DiscoverRegions] is ListChoices.Ready }
            vm.loadList(ServerList.DiscoverRegions)

            assertEquals(
                ListChoices.Ready(listOf(ListEntry("GB", "United Kingdom"))),
                ready.extras.lists[ServerList.DiscoverRegions],
            )
            assertEquals(1, seerr.count("GET", "/api/v1/regions"))
        }

    @Test
    fun `a list that fails to load is tried again on the next ask`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/languages", code = 500)
            val vm = viewModel()
            vm.awaitReady()

            vm.loadList(ServerList.Languages)
            vm.awaitReady { it.extras.lists[ServerList.Languages] == ListChoices.Failed }
            seerr.serve("GET /api/v1/languages", """[{"iso_639_1":"fr","english_name":"French"}]""")
            vm.loadList(ServerList.Languages)
            val ready = vm.awaitReady { it.extras.lists[ServerList.Languages] is ListChoices.Ready }

            assertEquals(ListChoices.Ready(listOf(ListEntry("fr", "French"))), ready.extras.lists[ServerList.Languages])
        }

    @Test
    fun `streaming regions come from the watch provider list`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/watchproviders/regions", """[{"iso_3166_1":"IE","english_name":"Ireland"}]""")
            val vm = viewModel()
            vm.awaitReady()

            vm.loadList(ServerList.StreamingRegions)
            val ready = vm.awaitReady { it.extras.lists[ServerList.StreamingRegions] is ListChoices.Ready }

            assertEquals(ListChoices.Ready(listOf(ListEntry("IE", "Ireland"))), ready.extras.lists[ServerList.StreamingRegions])
        }

    @Test
    fun `the jellyseerr lineage shows both regions and its own switches, never the proxy ones`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            val draft = vm.awaitReady().draft

            assertEquals("Home", draft.applicationTitle)
            assertEquals("GB", draft.discoverRegion)
            assertEquals("IE", draft.streamingRegion)
            assertEquals(false, draft.hideRequested)
            assertEquals(true, draft.specialEpisodes)
            assertEquals("https://yt.example", draft.youtubeUrl)
            assertNull(draft.trustProxy)
            assertNull(draft.csrfProtection)
            assertNull(draft.versionCheck)
            val extras = vm.awaitReady().extras
            assertEquals("old-key", extras.apiKey.key)
            assertFalse(extras.apiKey.revealed)
        }

    @Test
    fun `overseerr shows one region and the proxy switches`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            seerr.serve("GET /api/v1/settings/main", OVERSEERR_MAIN)
            val draft = viewModel().awaitReady().draft

            assertEquals("US", draft.discoverRegion)
            assertNull(draft.streamingRegion)
            assertNull(draft.hideRequested)
            assertNull(draft.specialEpisodes)
            assertNull(draft.youtubeUrl)
            assertEquals(true, draft.trustProxy)
            assertEquals(false, draft.csrfProtection)
            assertEquals(false, draft.partialRequests)
        }

    @Test
    fun `saving posts only the form's fields, never the key, and adopts the answer`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()
            seerr.serve("POST /api/v1/settings/main", LINEAGE_MAIN.replace("\"Home\"", "\"Cinema\""))

            vm.edit { it.copy(applicationTitle = "Cinema", hideAvailable = false) }
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/main")).jsonObject
            assertEquals("Cinema", sent.getValue("applicationTitle").jsonPrimitive.content)
            assertEquals("GB", sent.getValue("discoverRegion").jsonPrimitive.content)
            assertEquals("IE", sent.getValue("streamingRegion").jsonPrimitive.content)
            assertEquals("false", sent.getValue("hideAvailable").jsonPrimitive.content)
            assertNull(sent["apiKey"])
            assertNull(sent["defaultPermissions"])
            assertNull(sent["trustProxy"])
            val ready = vm.awaitReady()
            assertEquals("Cinema", ready.saved.applicationTitle)
            assertFalse(ready.dirty)
        }

    @Test
    fun `an address that is not a web url blocks the save`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(applicationUrl = "seerr.example") }
            vm.save()

            assertEquals(0, seerr.count("POST", "/api/v1/settings/main"))
        }

    /** The old key dies with the answer, so an app signed in with it must move to the new one in the same step. */
    @Test
    fun `regenerating reconnects an api-key session with the new key`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()

            val notice = awaitEvent(vm.events)
            vm.regenerateApiKey()
            assertTrue(notice.await() is EditorEvent.Notice)

            assertEquals(
                "new-key",
                vm
                    .awaitReady { it.extras.apiKey.key == "new-key" }
                    .extras.apiKey.key,
            )
            assertEquals(SeerrAuth.ApiKey("new-key"), connection.current().auth)
            val probe = seerr.received.last { it.url.encodedPath == "/api/v1/auth/me" }
            assertEquals("new-key", probe.headers["X-Api-Key"])
        }

    @Test
    fun `a server with an automatic blocklist has its settings, and saving sends them back`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            val draft = vm.awaitReady().draft
            assertEquals(false, draft.hideBlocklisted)
            assertEquals(BlocklistSettings(region = "", languages = "ja", tags = "9951,210024", tagsLimit = "50"), draft.blocklist)

            vm.edit { it.copy(hideBlocklisted = true, blocklist = it.blocklist?.copy(tags = "9951", tagsLimit = "100")) }
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/main")).jsonObject
            assertEquals("true", sent.getValue("hideBlocklisted").jsonPrimitive.content)
            assertEquals("9951", sent.getValue("blocklistedTags").jsonPrimitive.content)
            assertEquals("100", sent.getValue("blocklistedTagsLimit").jsonPrimitive.content)
            assertEquals("ja", sent.getValue("blocklistLanguage").jsonPrimitive.content)
        }

    @Test
    fun `a server without a blocklist has no blocklist settings and is sent none`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            seerr.serve("GET /api/v1/settings/main", OVERSEERR_MAIN)
            seerr.serve("POST /api/v1/settings/main", OVERSEERR_MAIN)
            val vm = viewModel()
            val draft = vm.awaitReady().draft
            assertNull(draft.blocklist)
            assertNull(draft.hideBlocklisted)

            vm.edit { it.copy(applicationTitle = "Cinema") }
            val saved = awaitEvent(vm.events)
            vm.save()
            assertEquals(EditorEvent.Saved, saved.await())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/main")).jsonObject
            assertNull(sent["blocklistedTags"])
            assertNull(sent["hideBlocklisted"])
        }

    @Test
    fun `the saved tags are named once, and a search lists what tmdb matches`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/keyword/9951", """{"id":9951,"name":"kaiju"}""")
            seerr.serve("GET /api/v1/keyword/210024", """{"id":210024,"name":"anime"}""")
            seerr.serve("GET /api/v1/search/keyword", """{"results":[{"id":4344,"name":"musical"},{"id":5,"name":null}]}""")
            val vm = viewModel()
            vm.awaitReady()

            vm.loadKeywordNames(listOf(9951, 210024))
            val named = vm.awaitReady { it.extras.keywords.names.size == 2 }
            vm.loadKeywordNames(listOf(9951, 210024))
            assertEquals(mapOf(9951 to "kaiju", 210024 to "anime"), named.extras.keywords.names)

            vm.searchKeywords("mus")
            val found = vm.awaitReady { it.extras.keywords.results != null }
            assertEquals(listOf(Keyword(4344, "musical")), found.extras.keywords.results)
            assertEquals("musical", found.extras.keywords.names[4344])
            assertEquals(1, seerr.count("GET", "/api/v1/keyword/9951"))

            vm.searchKeywords("")
            assertNull(
                vm
                    .awaitReady { it.extras.keywords.results == null }
                    .extras.keywords.results,
            )
        }

    @Test
    fun `a search cancelled by a newer query does not report a failure`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serveFrom("GET /api/v1/search/keyword", delayMillis = 200) { """{"results":[{"id":4344,"name":"musical"}]}""" }
            val vm = viewModel()
            vm.awaitReady()
            val failures = mutableListOf<Boolean>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.uiState.collect { state ->
                    if (state is ExtrasEditorUiState.Ready) failures += state.extras.keywords.failed
                }
            }

            vm.searchKeywords("mu")
            advanceTimeBy(KEYWORD_SEARCH_DEBOUNCE_MILLIS + 1)
            vm.awaitReady { it.extras.keywords.searching }
            vm.searchKeywords("mus")
            val found = vm.awaitReady { it.extras.keywords.results != null }

            assertEquals(listOf(Keyword(4344, "musical")), found.extras.keywords.results)
            assertFalse(failures.any { it })
        }
}
