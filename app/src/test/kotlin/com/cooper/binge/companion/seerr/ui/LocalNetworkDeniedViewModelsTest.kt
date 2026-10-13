package com.cooper.binge.companion.seerr.ui

import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.FakeIssueStore
import com.cooper.binge.companion.seerr.data.FakeRequestStore
import com.cooper.binge.companion.seerr.data.FakeUserStore
import com.cooper.binge.companion.seerr.seerr.PlexClientIdentity
import com.cooper.binge.companion.seerr.seerr.PlexPinDto
import com.cooper.binge.companion.seerr.seerr.PlexTvApi
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistReadCache
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistUiState
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistViewModel
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailViewModel
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.issues.IssuesViewModel
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailViewModel
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestsViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.BlocklistTagsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.BlocklistTagsViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.CacheUiState
import com.cooper.binge.companion.seerr.ui.settings.server.CacheViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.JobsViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.ServicesUiState
import com.cooper.binge.companion.seerr.ui.settings.server.ServicesViewModel
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserDetailViewModel
import com.cooper.binge.companion.seerr.ui.users.UsersUiState
import com.cooper.binge.companion.seerr.ui.users.UsersViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.LinkedAccountsUiState
import com.cooper.binge.companion.seerr.ui.users.settings.LinkedAccountsViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsUiState
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.deniedConnection
import com.cooper.binge.companion.seerr.util.FakeTitleDao
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.RecordingAnalytics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.milliseconds

/**
 * Every screen that reads the server, on a server the user keeps on their own network with Android's local-network
 * permission refused (#1507): its state is the one error the hub already answers with, [SeerrError.LocalNetworkDenied],
 * so the screen can show "Local network access is off" and Allow, not a "Can't reach the server" with a retry that cannot
 * work. The gate is the saved server's client, so each screen reaches it through the same connection it already uses.
 */
class LocalNetworkDeniedViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val dispatcher: CoroutineDispatcher get() = mainDispatcherRule.dispatcher

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 7, permissions = ADMIN)
    }

    @After
    fun tearDown() = seerr.close()

    /** The screen's state once it has settled on its failure, whichever sealed type that is. */
    private suspend fun <S : Any> StateFlow<S>.failure(isError: (S) -> SeerrError?): SeerrError = isError(first { isError(it) != null })!!

    private suspend fun TestScope.denied(): SeerrConnection = seerr.deniedConnection(this)

    @Test
    fun `the requests list`() =
        runTest {
            val ticker =
                object : Ticker() {
                    override suspend fun await(millis: Long) = kotlinx.coroutines.awaitCancellation()
                }
            val vm = RequestsViewModel(denied(), TitleCache(FakeTitleDao()), FakeRequestStore(), dispatcher, ticker)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.setScreenVisible(true)

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? RequestsUiState.Error)?.error })
        }

    @Test
    fun `a request's detail`() =
        runTest {
            val vm = RequestDetailViewModel(denied(), dispatcher, 11, RecordingAnalytics(), cache = FakeRequestStore())
            backgroundScope.launch { vm.uiState.collect {} }

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? RequestDetailUiState.Error)?.error })
        }

    @Test
    fun `the issues list`() =
        runTest {
            val ticker =
                object : Ticker() {
                    override suspend fun await(millis: Long) = kotlinx.coroutines.awaitCancellation()
                }
            val vm =
                IssuesViewModel(
                    denied(),
                    TitleCache(FakeTitleDao()),
                    FakeIssueStore(),
                    dispatcher,
                    RecordingAnalytics(),
                    minuteTicker = ticker,
                )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.setScreenVisible(true)

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? IssuesUiState.Error)?.error })
        }

    @Test
    fun `an issue's detail`() =
        runTest {
            val vm = IssueDetailViewModel(denied(), TitleCache(FakeTitleDao()), FakeIssueStore(), dispatcher, 31, RecordingAnalytics())
            backgroundScope.launch { vm.uiState.collect {} }

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? IssueDetailUiState.Error)?.error })
        }

    @Test
    fun `the users list`() =
        runTest {
            val vm = UsersViewModel(denied(), FakeUserStore(), dispatcher)
            backgroundScope.launch { vm.uiState.collect {} }

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? UsersUiState.Error)?.error })
        }

    @Test
    fun `a user's detail`() =
        runTest {
            val vm = UserDetailViewModel(denied(), TitleCache(FakeTitleDao()), FakeUserStore(), dispatcher, 8)
            backgroundScope.launch { vm.uiState.collect {} }

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? UserDetailUiState.Error)?.error })
        }

    @Test
    fun `a user's settings`() =
        runTest {
            val vm = UserSettingsViewModel(denied(), dispatcher, 8)
            backgroundScope.launch { vm.uiState.collect {} }

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? UserSettingsUiState.Error)?.error })
        }

    @Test
    fun `a user's linked accounts`() =
        runTest {
            val plexTv =
                object : PlexTvApi {
                    override suspend fun createPin(strong: Boolean): PlexPinDto = PlexPinDto(id = 5, code = "ABCD")

                    override suspend fun pin(id: Long): PlexPinDto = PlexPinDto(id = id, code = "ABCD")
                }
            val plex =
                PlexPinFlow(
                    identity = { PlexClientIdentity(identifier = "cid", product = "Binge Seerr", version = "0.1.0", device = "Pixel") },
                    apis = { plexTv },
                    pollInterval = 10.milliseconds,
                )
            val vm = LinkedAccountsViewModel(denied(), plex, dispatcher, 8)
            backgroundScope.launch { vm.uiState.collect {} }

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? LinkedAccountsUiState.Error)?.error })
        }

    @Test
    fun `the blocklist`() =
        runTest {
            val vm = BlocklistViewModel(denied(), TitleCache(FakeTitleDao()), dispatcher, BlocklistReadCache(), RecordingAnalytics())
            backgroundScope.launch { vm.uiState.collect {} }

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? BlocklistUiState.Error)?.error })
        }

    @Test
    fun `the blocklisted tags`() =
        runTest {
            val vm = BlocklistTagsViewModel(denied(), dispatcher, backgroundScope)
            backgroundScope.launch { vm.uiState.collect {} }

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? BlocklistTagsUiState.Error)?.error })
        }

    @Test
    fun `the server's services`() =
        runTest {
            val vm = ServicesViewModel(denied(), dispatcher)
            vm.reload()

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? ServicesUiState.Error)?.error })
        }

    @Test
    fun `the server's cache page`() =
        runTest {
            val vm = CacheViewModel(denied(), dispatcher)

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? CacheUiState.Error)?.error })
        }

    @Test
    fun `the server's jobs page`() =
        runTest {
            val vm = JobsViewModel(denied(), dispatcher)

            assertEquals(SeerrError.LocalNetworkDenied, vm.uiState.failure { (it as? JobsUiState.Error)?.error })
        }
}
