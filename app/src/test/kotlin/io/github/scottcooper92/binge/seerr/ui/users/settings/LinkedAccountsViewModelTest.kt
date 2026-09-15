package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.PlexPinFlow
import io.github.scottcooper92.binge.seerr.seerr.PlexClientIdentity
import io.github.scottcooper92.binge.seerr.seerr.PlexPinDto
import io.github.scottcooper92.binge.seerr.seerr.PlexTvApi
import io.github.scottcooper92.binge.seerr.ui.LinkFlow
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.milliseconds

class LinkedAccountsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    /** plex.tv as two calls: the PIN, then the poll that answers with a token once [approved]. */
    private var approved = false
    private val plexTv =
        object : PlexTvApi {
            override suspend fun createPin(): PlexPinDto = PlexPinDto(id = 5, code = "ABCD")

            override suspend fun pin(id: Long): PlexPinDto = PlexPinDto(id = id, code = "ABCD", authToken = "tok".takeIf { approved })
        }

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 8, permissions = REQUEST)
        seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","jellyfinUsername":"ana","jellyfinUserId":"j-1","userType":3}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): LinkedAccountsViewModel {
        val plex =
            PlexPinFlow(
                identity = { PlexClientIdentity(identifier = "cid", product = "Binge Seerr", version = "0.1.0", device = "Pixel") },
                apis = { plexTv },
                pollInterval = 10.milliseconds,
            )
        val vm = LinkedAccountsViewModel(seerr.connection(this), plex, 8)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun LinkedAccountsViewModel.awaitReady(match: (LinkedAccountsUiState.Ready) -> Boolean = { !it.busy }) =
        uiState.first { it is LinkedAccountsUiState.Ready && match(it) } as LinkedAccountsUiState.Ready

    @Test
    fun `the page reads each account's link state from the user record and Quick Connect from the server`() =
        runTest {
            val ready = viewModel().awaitReady()
            assertEquals(LinkedAccount(UserOrigin.Plex, linked = false), ready.plex)
            assertEquals(LinkedAccount(UserOrigin.Jellyfin, linked = true, linkedAs = "ana"), ready.mediaServer)
            assertTrue(ready.canQuickConnect)

            seerr.viewer(id = 8, permissions = REQUEST, version = "2.5.0", settings = """{"mediaServerType":3}""")
            val emby = viewModel().awaitReady()
            assertEquals(UserOrigin.Emby, emby.mediaServer?.origin)
            assertFalse(emby.canQuickConnect)
        }

    @Test
    fun `linking Plex shows the PIN, then posts the approved token and re-reads the record`() =
        runTest {
            seerr.serve("POST /api/v1/user/8/settings/linked-accounts/plex")
            val vm = viewModel()
            vm.awaitReady()

            vm.linkPlex()
            val link = vm.awaitReady { it.link != null }.link as LinkFlow.Plex
            assertEquals("ABCD", link.code)
            assertTrue(link.launchPending)
            vm.plexLaunched()
            assertFalse((vm.awaitReady { it.link != null }.link as LinkFlow.Plex).launchPending)

            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","plexId":77,"plexUsername":"ana_plex","userType":3}""")
            val linked = awaitEvent(vm.events)
            approved = true
            assertEquals(LinkedAccountsEvent.Linked, linked.await())
            assertEquals("""{"authToken":"tok"}""", seerr.body("POST", "/api/v1/user/8/settings/linked-accounts/plex"))
            val after = vm.awaitReady()
            assertEquals("ana_plex", after.plex.linkedAs)
            assertNull(after.link)
        }

    @Test
    fun `cancelling a link stops the poll, and an expired code is its own event`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()
            vm.linkPlex()
            vm.awaitReady { it.link != null }
            vm.cancelLink()
            assertNull(vm.awaitReady().link)

            seerr.serve("POST /api/v1/auth/jellyfin/quickconnect/initiate", """{"code":"123456","secret":"s3cret"}""")
            seerr.serve("GET /api/v1/auth/jellyfin/quickconnect/check", code = 404)
            val linkExpired = awaitEvent(vm.events)
            vm.linkQuickConnect()
            assertEquals(LinkedAccountsEvent.LinkExpired, linkExpired.await())
        }

    @Test
    fun `Quick Connect polls the code and links with its secret, and credentials link directly`() =
        runTest {
            seerr.serve("POST /api/v1/auth/jellyfin/quickconnect/initiate", """{"code":"123456","secret":"s3cret"}""")
            seerr.serve("GET /api/v1/auth/jellyfin/quickconnect/check", """{"authenticated":true}""")
            seerr.serve("POST /api/v1/user/8/settings/linked-accounts/jellyfin/quickconnect")
            seerr.serve("POST /api/v1/user/8/settings/linked-accounts/jellyfin")
            val vm = viewModel()
            vm.awaitReady()

            val linkedByQuickConnect = awaitEvent(vm.events)
            vm.linkQuickConnect()
            assertEquals(LinkedAccountsEvent.Linked, linkedByQuickConnect.await())
            assertEquals("""{"secret":"s3cret"}""", seerr.body("POST", "/api/v1/user/8/settings/linked-accounts/jellyfin/quickconnect"))

            vm.awaitReady()
            val linkedByCredentials = awaitEvent(vm.events)
            vm.linkJellyfin(" ana ", "pw")
            assertEquals(LinkedAccountsEvent.Linked, linkedByCredentials.await())
            assertEquals("""{"username":"ana","password":"pw"}""", seerr.body("POST", "/api/v1/user/8/settings/linked-accounts/jellyfin"))
        }

    @Test
    fun `unlinking deletes the link and re-reads the record`() =
        runTest {
            seerr.serve("DELETE /api/v1/user/8/settings/linked-accounts/jellyfin")
            val vm = viewModel()
            vm.awaitReady()

            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","userType":3}""")
            val unlinked = awaitEvent(vm.events)
            vm.unlinkMediaServer()
            assertEquals(LinkedAccountsEvent.Unlinked, unlinked.await())
            assertEquals(1, seerr.count("DELETE", "/api/v1/user/8/settings/linked-accounts/jellyfin"))
            assertFalse(vm.awaitReady().mediaServer?.linked == true)
        }
}
