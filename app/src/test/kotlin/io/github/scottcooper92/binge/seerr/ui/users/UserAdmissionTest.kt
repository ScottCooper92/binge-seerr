package io.github.scottcooper92.binge.seerr.ui.users

import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import io.github.scottcooper92.binge.seerr.data.FakeUserStore
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.REQUEST
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import kotlin.time.Duration.Companion.seconds

private const val USER_PAGE =
    """{"pageInfo":{"pages":1,"results":1},"results":[{"id":7,"displayName":"Scott","permissions":2,"jellyfinUserId":"j-7"}]}"""

/** Adding users through the browser: the local-account form, and the import picker over the media server's accounts. */
class UserAdmissionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()
    private val cache = FakeUserStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.viewer(id = 7, permissions = ADMIN, settings = """{"mediaServerType":2,"emailEnabled":true,"applicationUrl":"https://s"}""")
        seerr.serve("GET /api/v1/user", USER_PAGE)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): UsersViewModel {
        val vm = UsersViewModel(seerr.connection(this), cache)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun UsersViewModel.awaitReady(match: (UsersUiState.Ready) -> Boolean = { true }): UsersUiState.Ready =
        uiState.first { it is UsersUiState.Ready && match(it) } as UsersUiState.Ready

    @Test
    fun `a manager on a Jellyfin server may add users, with a generated password where the server can email one`() =
        runTest {
            val ready = viewModel().awaitReady { it.canAdmit }
            assertEquals(UserOrigin.Jellyfin, ready.importSource)
            assertTrue(ready.canGeneratePassword)

            seerr.viewer(id = 8, permissions = REQUEST, settings = """{"emailEnabled":true}""")
            val plain = viewModel().awaitReady { it.importSource == null }
            assertFalse(plain.canAdmit)
            assertFalse(plain.canGeneratePassword)
        }

    @Test
    fun `creating posts the account, with the password left to the server when asked, then refreshes the list`() =
        runTest {
            seerr.serve("POST /api/v1/user", """{"id":9,"displayName":"Ana","email":"ana@example.com","permissions":32}""")
            val vm = viewModel()
            vm.awaitReady()
            vm.users.asSnapshot()
            val listReads = seerr.count("GET", "/api/v1/user")

            vm.admission.startCreate(canGeneratePassword = true)
            vm.admission.editDraft { it.copy(email = "ana", username = "ana") }
            vm.admission.create()
            assertEquals(0, seerr.count("POST", "/api/v1/user"))

            vm.admission.editDraft { it.copy(email = " ana@example.com ", password = "longenough") }
            vm.admission.create()
            assertEquals(UsersEvent.UserCreated("Ana"), vm.events.first())
            assertEquals("""{"email":"ana@example.com","username":"ana","password":"longenough"}""", seerr.body("POST", "/api/v1/user"))
            assertNull(vm.awaitReady().admission)
            vm.users.asSnapshot()

            // The refresh is a new Pager generation, and `cachedIn` can hand a fresh subscriber the
            // PREVIOUS generation's loaded data before the new one reaches its cache slot - so the
            // read lands shortly after the snapshot rather than during it. Sampling the count once
            // caught that window about a quarter of the time. Real time, because Main here is
            // Dispatchers.Unconfined and the load runs on OkHttp's threads, not the test scheduler.
            withContext(Dispatchers.Default) {
                withTimeout(5.seconds) {
                    while (seerr.count("GET", "/api/v1/user") <= listReads) delay(10.milliseconds)
                }
            }
            assertTrue(seerr.count("GET", "/api/v1/user") > listReads)

            vm.admission.startCreate(canGeneratePassword = true)
            vm.admission.editDraft { it.copy(email = "bo@example.com", username = "bo", generatePassword = true) }
            vm.admission.create()
            assertEquals(UsersEvent.UserCreated("Ana"), vm.events.first())
            assertEquals("""{"email":"bo@example.com","username":"bo"}""", seerr.body("POST", "/api/v1/user"))
        }

    @Test
    fun `the Jellyfin picker hides the accounts already on the server and imports the ticked ones`() =
        runTest {
            seerr.serve(
                "GET /api/v1/settings/jellyfin/users",
                """[{"id":"j-7","username":"scott"},{"id":"j-8","username":"ana","email":"ana@example.com"},{"id":"j-9","username":"bo"}]""",
            )
            seerr.serve("POST /api/v1/user/import-from-jellyfin", """[{"id":10,"displayName":"Ana"},{"id":11,"displayName":"Bo"}]""")
            val vm = viewModel()
            vm.awaitReady()

            vm.admission.start()
            vm.admission.startImport(UserOrigin.Jellyfin)
            val picker =
                (
                    vm
                        .awaitReady {
                            (it.admission as? UserAdmissionState.Importing)?.picker?.candidates != null
                        }.admission as UserAdmissionState.Importing
                ).picker
            assertEquals(listOf("j-8", "j-9"), picker.candidates?.map { it.id })
            assertEquals("ana@example.com", picker.candidates?.first()?.email)

            vm.admission.selectAllCandidates(true)
            vm.admission.toggleCandidate("j-9")
            vm.admission.toggleCandidate("j-9")
            vm.admission.import()
            assertEquals(UsersEvent.UsersImported(2), vm.events.first())
            assertEquals("""{"jellyfinUserIds":["j-8","j-9"]}""", seerr.body("POST", "/api/v1/user/import-from-jellyfin"))
            assertNull(vm.awaitReady().admission)
        }

    @Test
    fun `the Plex picker takes the server's list as it is, and counts what the lineage's answer says was created`() =
        runTest {
            seerr.viewer(id = 7, permissions = ADMIN, version = "1.33.0", settings = "{}")
            seerr.serve("GET /api/v1/settings/plex/users", """[{"id":"p-1","title":"Ana","username":"ana","thumb":"https://t/a.png"}]""")
            seerr.serve("POST /api/v1/user/import-from-plex", """{"createdUsers":[],"refreshedUsers":[{"id":3}]}""")
            val vm = viewModel()
            assertEquals(UserOrigin.Plex, vm.awaitReady { it.importSource != null }.importSource)

            vm.admission.startImport(UserOrigin.Plex)
            vm.awaitReady { (it.admission as? UserAdmissionState.Importing)?.picker?.candidates?.size == 1 }
            vm.admission.toggleCandidate("p-1")
            vm.admission.import()
            assertEquals(UsersEvent.UsersImported(0), vm.events.first())
            assertEquals("""{"plexIds":["p-1"]}""", seerr.body("POST", "/api/v1/user/import-from-plex"))
        }
}
