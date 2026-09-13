package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeneralSettingsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
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
        val vm = GeneralSettingsViewModel(seerr.connection(this), 8)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun GeneralSettingsViewModel.awaitReady(): EditorUiState.Ready<GeneralSettings> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<GeneralSettings>

    @Test
    fun `the record reads as text fields with the server's defaults alongside, and a manager may edit the quotas`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val draft = viewModel().awaitReady().draft

            assertEquals("Ana", draft.displayName)
            assertEquals("5", draft.movieQuotaLimit)
            assertEquals("", draft.tvQuotaLimit)
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

    @Test
    fun `saving posts the draft with a blank quota as null, then adopts the server's re-read`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(displayName = "Ana B", movieQuotaLimit = "", tvQuotaLimit = "x") }
            vm.save()
            assertEquals(0, seerr.count("POST", "/api/v1/user/8/settings/main"))

            vm.edit { it.copy(tvQuotaLimit = "3") }
            seerr.serve("GET /api/v1/user/8/settings/main", """{"username":"Ana B","tvQuotaLimit":3,"tvQuotaDays":7}""")
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/user/8/settings/main")).jsonObject
            assertEquals("Ana B", sent.getValue("username").jsonPrimitive.content)
            assertNull(sent["movieQuotaLimit"])
            assertEquals("3", sent.getValue("tvQuotaLimit").jsonPrimitive.content)
            val ready = vm.awaitReady()
            assertEquals("Ana B", ready.saved.displayName)
            assertFalse(ready.dirty)
        }
}
