package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.SAVE_AS_MADE_DELAY_MILLIS
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val LINEAGE_MAIN =
    """{"applicationTitle":"Home","localLogin":true,"mediaServerLogin":false,"newPlexLogin":true,"defaultPermissions":32,
       "defaultQuotas":{"movie":{"quotaLimit":5,"quotaDays":14},"tv":{"quotaLimit":0,"quotaDays":0}}}"""

private const val OVERSEERR_MAIN = """{"applicationTitle":"Home","localLogin":false,"newPlexLogin":false,"defaultPermissions":32}"""

class ServerUsersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        seerr.start()
        seerr.serve("GET /api/v1/settings/main", LINEAGE_MAIN)
        seerr.serve("POST /api/v1/settings/main", LINEAGE_MAIN)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): ServerUsersViewModel {
        val vm = ServerUsersViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, backgroundScope)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun ServerUsersViewModel.awaitReady(
        where: (ExtrasEditorUiState.Ready<ServerUsersSettings, ServerUsersExtras>) -> Boolean = { true },
    ): ExtrasEditorUiState.Ready<ServerUsersSettings, ServerUsersExtras> =
        uiState.first {
            it is ExtrasEditorUiState.Ready && !it.saving && where(it)
        } as ExtrasEditorUiState.Ready<ServerUsersSettings, ServerUsersExtras>

    @Test
    fun `the jellyseerr lineage has its media server switch and its limits`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val ready = viewModel().awaitReady()

            assertEquals(
                ServerUsersSettings(
                    localLogin = true,
                    mediaServerLogin = false,
                    newMediaServerLogin = true,
                    movieLimit = 5,
                    movieDays = 14,
                    tvLimit = 0,
                    tvDays = DEFAULT_LIMIT_DAYS,
                ),
                ready.draft,
            )
            assertEquals(setOf(ManageablePermission.Request), ready.extras.defaultPermissions)
        }

    @Test
    fun `overseerr has no media server switch and no limits set`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            seerr.serve("GET /api/v1/settings/main", OVERSEERR_MAIN)
            val ready = viewModel().awaitReady()

            assertNull(ready.draft.mediaServerLogin)
            assertEquals(false, ready.draft.localLogin)
            assertEquals(0, ready.draft.movieLimit)
            assertEquals(SeerrMediaServer.Plex, ready.extras.mediaServer)
        }

    @Test
    fun `an edit is written by itself, sending only this page's fields`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(tvLimit = 3, tvDays = 30) }
            // Users saves as it changes: the edit goes out once the delay has passed.
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.awaitReady { !it.dirty }

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/main")).jsonObject
            val tv =
                sent
                    .getValue("defaultQuotas")
                    .jsonObject
                    .getValue("tv")
                    .jsonObject
            assertEquals(3, tv.getValue("quotaLimit").jsonPrimitive.int)
            assertEquals(30, tv.getValue("quotaDays").jsonPrimitive.int)
            assertEquals("false", sent.getValue("mediaServerLogin").jsonPrimitive.content)
            assertNull(sent["applicationTitle"])
            assertNull(sent["defaultPermissions"])
        }

    @Test
    fun `turning every way in off is never written`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()

            vm.edit { it.copy(localLogin = false) }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()

            assertEquals(false, vm.awaitReady().draft.valid)
            assertEquals(0, seerr.count("POST", "/api/v1/settings/main"))
        }

    @Test
    fun `returning from the default permissions reads them again without dropping an edit`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val vm = viewModel()
            vm.awaitReady()
            vm.edit { it.copy(movieLimit = 9) }

            seerr.serve("GET /api/v1/settings/main", LINEAGE_MAIN.replace("\"defaultPermissions\":32", "\"defaultPermissions\":160"))
            vm.refreshDefaultPermissions()
            vm.awaitReady { it.extras.defaultPermissions.size == 2 }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.awaitReady { !it.dirty }

            // Re-reading the defaults did not drop the edit: it went out with the page's own write.
            val movie =
                Json
                    .parseToJsonElement(seerr.body("POST", "/api/v1/settings/main"))
                    .jsonObject
                    .getValue("defaultQuotas")
                    .jsonObject
                    .getValue("movie")
                    .jsonObject
            assertEquals(9, movie.getValue("quotaLimit").jsonPrimitive.int)
        }
}
