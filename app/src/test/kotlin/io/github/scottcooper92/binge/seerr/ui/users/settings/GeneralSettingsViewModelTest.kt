package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerListCatalog
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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

class GeneralSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        seerr.start()
        seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$REQUEST,"userType":3}""")
        seerr.serve(
            "GET /api/v1/user/8/settings/main",
            """{"username":"Ana","email":"ana@example.com","discordId":"","locale":"en","region":"GB","originalLanguage":"",
               "movieQuotaLimit":5,"movieQuotaDays":7,"globalMovieQuotaLimit":10,"globalMovieQuotaDays":7,
               "globalTvQuotaLimit":0,"globalTvQuotaDays":7,"watchlistSyncMovies":true}""",
        )
        seerr.serve("POST /api/v1/user/8/settings/main")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): GeneralSettingsViewModel {
        val connection = seerr.connection(this)
        val vm = GeneralSettingsViewModel(connection, ServerListCatalog(connection), mainDispatcherRule.dispatcher, backgroundScope, 8)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun GeneralSettingsViewModel.awaitReady(): ExtrasEditorUiState.Ready<GeneralSettings, UserGeneralExtras> =
        uiState.first { it is ExtrasEditorUiState.Ready && !it.saving } as ExtrasEditorUiState.Ready<GeneralSettings, UserGeneralExtras>

    /** A user's General saves as it changes: the change goes out once the delay has passed, and the re-read is adopted. */
    private suspend fun TestScope.awaitWritten(vm: GeneralSettingsViewModel) {
        advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
        vm.uiState.first { it is ExtrasEditorUiState.Ready && !it.dirty }
    }

    @Test
    fun `the record reads with the server's defaults alongside, and a manager may edit the quotas`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val draft = viewModel().awaitReady().draft

            assertEquals("Ana", draft.displayName)
            assertEquals(UserOrigin.Jellyfin, draft.accountType)
            assertEquals(UserRole.User, draft.role)
            assertTrue(draft.movieQuotaOverride)
            assertEquals(5, draft.movieQuotaLimit)
            // No override of its own: the switch is off, and turning it on starts from the server's quota.
            assertFalse(draft.tvQuotaOverride)
            assertEquals(0, draft.tvQuotaLimit)
            assertEquals(7, draft.tvQuotaDays)
            assertEquals(QuotaDefault(limit = 10, days = 7), draft.defaultMovieQuota)
            assertEquals(QuotaDefault(limit = 0, days = 7), draft.defaultTvQuota)
            assertEquals(true, draft.watchlistSyncMovies)
            assertNull(draft.watchlistSyncTv)
            assertTrue(draft.canEditQuotas)
            assertTrue(draft.canEditEmail)
        }

    @Test
    fun `a user editing themself keeps a media-server email read-only and cannot touch the quotas`() =
        runTest {
            seerr.viewer(id = 8, permissions = REQUEST)
            val draft = viewModel().awaitReady().draft
            assertFalse(draft.canEditQuotas)
            assertFalse(draft.canEditEmail)
        }

    /** The server keeps the old quota for a manager's own record or another manager's, so the editor is not offered (#1014). */
    @Test
    fun `a manager may not edit the quotas of themself or of another manager`() =
        runTest {
            seerr.viewer(id = 8, permissions = ADMIN)
            assertFalse(viewModel().awaitReady().draft.canEditQuotas)

            seerr.viewer(id = 1, permissions = MANAGE_USERS)
            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$MANAGE_USERS,"userType":3}""")
            assertFalse(viewModel().awaitReady().draft.canEditQuotas)
        }

    @Test
    fun `the placeholder name is what the server would fall back to, never the display name being cleared`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve(
                "GET /api/v1/user/8",
                """{"id":8,"displayName":"Ana","username":"Ana","jellyfinUsername":"ana.j","email":"ana@example.com","userType":3}""",
            )
            assertEquals("ana.j", viewModel().awaitReady().draft.fallbackName)
        }

    @Test
    fun `a user with no media-server account falls back to their email`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","username":"Ana","email":"ana@example.com","userType":2}""")
            assertEquals("ana@example.com", viewModel().awaitReady().draft.fallbackName)
        }

    @Test
    fun `the web client's email rule, where a Jellyfin user may go without, a local user or the owner may not`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            assertFalse(viewModel().awaitReady().draft.emailRequired)

            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","userType":2,"permissions":$ADMIN}""")
            val local = viewModel().awaitReady().draft
            assertTrue(local.emailRequired)
            assertEquals(UserRole.Admin, local.role)
            assertFalse(local.copy(email = "").valid)
        }

    @Test
    fun `the server's own Discover settings come alongside, for the blank choices to name`() =
        runTest {
            seerr.viewer(
                id = 1,
                permissions = ADMIN,
                settings = """{"locale":"fr","discoverRegion":"FR","streamingRegion":"BE","originalLanguage":"fr|en"}""",
            )
            assertEquals(
                ServerDiscoverDefaults(locale = "fr", region = "FR", streamingRegion = "BE", originalLanguage = "fr|en"),
                viewModel().awaitReady().extras.serverDefaults,
            )
        }

    @Test
    fun `a change sends an override's quota and nulls one that is off, then adopts the server's re-read`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(displayName = "Ana B", movieQuotaOverride = false, tvQuotaOverride = true, tvQuotaLimit = 3) }
            seerr.serve("GET /api/v1/user/8/settings/main", """{"username":"Ana B","tvQuotaLimit":3,"tvQuotaDays":7}""")
            awaitWritten(vm)

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/user/8/settings/main")).jsonObject
            assertEquals("Ana B", sent.getValue("username").jsonPrimitive.content)
            assertNull(sent["movieQuotaLimit"])
            assertNull(sent["movieQuotaDays"])
            assertEquals("3", sent.getValue("tvQuotaLimit").jsonPrimitive.content)
            assertEquals("7", sent.getValue("tvQuotaDays").jsonPrimitive.content)
            val ready = vm.awaitReady()
            assertEquals("Ana B", ready.saved.displayName)
            assertTrue(ready.saved.tvQuotaOverride)
            assertFalse(ready.dirty)
        }

    @Test
    fun `the Jellyseerr lineage's split regions read and write, and the Discord ID this page hides survives the save`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve(
                "GET /api/v1/user/8/settings/main",
                """{"username":"Ana","discoverRegion":"GB","streamingRegion":"IE","locale":"en","discordId":"1234"}""",
            )
            val vm = viewModel()
            val draft = vm.awaitReady().draft
            assertEquals("GB", draft.region)
            assertEquals("IE", draft.streamingRegion)

            vm.edit { it.copy(region = "US", streamingRegion = "all") }
            awaitWritten(vm)

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/user/8/settings/main")).jsonObject
            assertEquals("US", sent.getValue("discoverRegion").jsonPrimitive.content)
            assertEquals("US", sent.getValue("region").jsonPrimitive.content)
            assertEquals("all", sent.getValue("streamingRegion").jsonPrimitive.content)
            assertEquals("1234", sent.getValue("discordId").jsonPrimitive.content)
        }

    @Test
    fun `Overseerr has no streaming region, so the page shows none and sends none`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            val vm = viewModel()
            assertNull(vm.awaitReady().draft.streamingRegion)

            vm.edit { it.copy(displayName = "Ana B") }
            awaitWritten(vm)
            assertNull(Json.parseToJsonElement(seerr.body("POST", "/api/v1/user/8/settings/main")).jsonObject["streamingRegion"])
        }

    @Test
    fun `a required email cleared is never written`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            // A local account, whose email the web client requires; the fixture's Jellyfin account may go without one.
            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$REQUEST,"userType":2}""")
            val vm = viewModel()
            assertTrue(vm.awaitReady().draft.emailRequired)

            vm.edit { it.copy(email = "") }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()

            // A write goes out on OkHttp's threads, so a count taken at once can miss one on its way (#986).
            assertFalse(seerr.awaitCountHoldingTime("POST", "/api/v1/user/8/settings/main", moreThan = 0))
        }

    @Test
    fun `an email the account may go without is cleared and written`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            assertFalse(vm.awaitReady().draft.emailRequired)

            vm.edit { it.copy(email = "") }
            awaitWritten(vm)

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/user/8/settings/main")).jsonObject
            // A blank address is sent as no address.
            assertTrue(sent["email"].let { it == null || it is JsonNull })
        }
}
