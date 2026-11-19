package com.cooper.binge.companion.seerr.ui.discover.request

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestAllResult
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.discover.request.RequestSheetRepository
import com.cooper.binge.companion.seerr.discover.request.RequestSubmission
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private val TARGET = RequestSheetTarget(TitleKind.Movie, tmdbId = 603, title = "The Matrix")
private val STANDARD = RequestServer(1, "Radarr", is4k = false, isDefault = true, profileId = 4, rootFolder = "/movies")
private val UHD = RequestServer(2, "Radarr 4K", is4k = true, isDefault = false, profileId = 7, rootFolder = "/uhd")
private val CHOICES = RequestChoices(listOf(RequestChoice(4, "HD"), RequestChoice(5, "Any")), listOf("/movies", "/more"))
private val PLAIN = RequestAccess(may4k = false, mayChooseDestination = false)
private val ADVANCED = RequestAccess(may4k = true, mayChooseDestination = true)

private class FakeRepository(
    var access: RequestAccess,
    var quota: RequestQuota? = null,
    var servers: List<RequestServer> = emptyList(),
    var failAccess: Throwable? = null,
    var failQuota: Throwable? = null,
    var failServers: Throwable? = null,
    var failChoices: Throwable? = null,
    var failSubmit: Throwable? = null,
    var result: RequestResult = RequestResult.Created,
) : RequestSheetRepository {
    val submissions = mutableListOf<RequestSubmission>()

    override suspend fun access(kind: TitleKind): RequestAccess = failAccess?.let { throw it } ?: access

    override suspend fun quota(kind: TitleKind): RequestQuota? = failQuota?.let { throw it } ?: quota

    override suspend fun servers(kind: TitleKind): List<RequestServer> = failServers?.let { throw it } ?: servers

    override suspend fun choices(
        kind: TitleKind,
        serverId: Int,
    ): RequestChoices = failChoices?.let { throw it } ?: CHOICES

    var allResult: RequestAllResult? = null

    override suspend fun submitAll(submissions: List<RequestSubmission>): RequestAllResult {
        this.submissions += submissions
        return allResult ?: RequestAllResult(created = submissions.size, alreadyThere = 0, failure = null)
    }

    override suspend fun submit(submission: RequestSubmission): RequestResult {
        submissions += submission
        failSubmit?.let { throw it }
        return result
    }
}

private fun refusal(
    code: Int,
    message: String,
) = HttpException(Response.error<Any>(code, """{"message":"$message"}""".toResponseBody("application/json".toMediaType())))

class RequestSheetViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel(
        repository: FakeRepository,
        target: RequestSheetTarget = TARGET,
    ) = RequestSheetViewModel(repository, target)

    private fun RequestSheetViewModel.ready() = uiState.value as RequestSheetUiState.Ready

    private fun advanced(quota: RequestQuota? = RequestQuota(10, 2, 7)) = FakeRepository(ADVANCED, quota, listOf(STANDARD, UHD))

    @Test
    fun `a failed load is an error screen that retries`() {
        val repository = FakeRepository(PLAIN, failAccess = IOException("down"))
        val vm = viewModel(repository)

        assertTrue(vm.uiState.value is RequestSheetUiState.Error)

        repository.failAccess = null
        vm.load()

        assertTrue(vm.uiState.value is RequestSheetUiState.Ready)
    }

    @Test
    fun `a user with no destination to choose sends a plain request`() =
        runTest {
            val repository = FakeRepository(PLAIN)
            val vm = viewModel(repository)
            assertNull(vm.ready().pick)
            assertFalse(vm.ready().choosesDestination)

            val event = awaitEvent(vm.events)
            vm.submit()

            assertEquals(RequestSheetEvent.Done(RequestResult.Created), event.await())
            assertEquals(
                RequestSubmission(TitleKind.Movie, 603, emptyList(), is4k = false, destination = null),
                repository.submissions.single(),
            )
        }

    @Test
    fun `the default server is preselected and its lists fill in`() {
        val vm = viewModel(advanced())

        val pick = vm.ready().pick!!
        assertEquals(1, pick.serverId)
        assertEquals(ChoicesState.Ready(CHOICES), pick.choices)
        assertEquals(4, pick.profileId)
        assertEquals("/movies", pick.rootFolder)
    }

    @Test
    fun `an untouched sheet sends no destination, so the server's own rules apply`() =
        runTest {
            val repository = advanced()
            val vm = viewModel(repository)
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            assertNull(repository.submissions.single().destination)
        }

    @Test
    fun `a read still in flight holds the request`() {
        val loading = RequestSheetUiState.Ready(TARGET, ADVANCED, quota = QuotaState.Known(null))

        assertTrue(loading.servers is ServersState.Loading)
        assertFalse(loading.canSubmit)
    }

    @Test
    fun `a failed servers read still sends, with no destination`() =
        runTest {
            val repository = advanced().apply { failServers = IOException("down") }
            val vm = viewModel(repository)
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            assertNull(repository.submissions.single().destination)
        }

    @Test
    fun `a user who may choose sends the chosen destination`() =
        runTest {
            val repository = advanced()
            val vm = viewModel(repository)
            vm.selectProfile(5)
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            val destination = repository.submissions.single().destination
            assertEquals(1, destination?.serverId)
            assertEquals(5, destination?.profileId)
            assertEquals("/movies", destination?.rootFolder)
        }

    @Test
    fun `changing the root folder sends the whole pick`() =
        runTest {
            val repository = advanced()
            val vm = viewModel(repository)
            vm.selectRootFolder("/more")
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            val destination = repository.submissions.single().destination
            assertEquals(1, destination?.serverId)
            assertEquals(4, destination?.profileId)
            assertEquals("/more", destination?.rootFolder)
        }

    @Test
    fun `switching to 4K drops a change made for the other version`() =
        runTest {
            val repository = advanced()
            val vm = viewModel(repository)
            vm.selectProfile(5)
            vm.setUse4k(true)
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            assertNull(repository.submissions.single().destination)
        }

    @Test
    fun `the 4K switch moves to a 4K server and back`() {
        val vm = viewModel(advanced())

        vm.setUse4k(true)
        assertEquals(2, vm.ready().pick?.serverId)
        assertEquals(listOf(UHD), vm.ready().servers4k)

        vm.setUse4k(false)
        assertEquals(1, vm.ready().pick?.serverId)
    }

    @Test
    fun `a pick that the new server does not offer falls back to its first`() {
        val vm = viewModel(advanced())

        vm.selectProfile(5)
        vm.selectRootFolder("/more")
        assertEquals(5, vm.ready().pick?.profileId)
        assertEquals("/more", vm.ready().pick?.rootFolder)

        vm.setUse4k(true)
        assertEquals(4, vm.ready().pick?.profileId)
        assertEquals("/movies", vm.ready().pick?.rootFolder)
    }

    @Test
    fun `a quota that fails to read is shown as failed, keeps Request enabled and reads again`() {
        val repository = advanced().apply { failQuota = IOException("down") }
        val vm = viewModel(repository)

        assertEquals(QuotaState.Failed, vm.ready().quota)
        assertTrue(vm.ready().canSubmit)

        repository.failQuota = null
        vm.retry(RequestRead.Quota)

        assertEquals(QuotaState.Known(RequestQuota(10, 2, 7)), vm.ready().quota)
    }

    @Test
    fun `an unmetered kind has no quota row`() {
        val vm = viewModel(advanced(quota = null))

        assertEquals(QuotaState.Known(null), vm.ready().quota)
    }

    @Test
    fun `a spent quota holds the submit`() {
        val repository = advanced(RequestQuota(10, 0, 7))
        val vm = viewModel(repository)

        assertFalse(vm.ready().canSubmit)
        vm.submit()

        assertTrue(repository.submissions.isEmpty())
    }

    @Test
    fun `servers that fail to read are failed, still allow a plain request, and read again`() {
        val repository = advanced().apply { failServers = IOException("down") }
        val vm = viewModel(repository)

        assertEquals(ServersState.Failed, vm.ready().servers)
        assertTrue(vm.ready().choosesDestination)
        assertTrue(vm.ready().canSubmit)
        assertNull(vm.ready().destination)

        repository.failServers = null
        vm.retry(RequestRead.Servers)

        assertTrue(vm.ready().servers is ServersState.Ready)
        assertTrue(vm.ready().canSubmit)
    }

    @Test
    fun `a picked server whose lists fail to load is still the server the request names`() =
        runTest {
            val second = RequestServer(3, "Radarr 2", is4k = false, isDefault = false, profileId = 6, rootFolder = "/b")
            val repository = FakeRepository(ADVANCED, RequestQuota(10, 2, 7), listOf(STANDARD, second, UHD))
            val vm = viewModel(repository)
            repository.failChoices = IOException("down")
            vm.selectServer(3)
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            val destination = repository.submissions.single().destination
            assertEquals(3, destination?.serverId)
            assertEquals(6, destination?.profileId)
            assertEquals("/b", destination?.rootFolder)
        }

    @Test
    fun `switching to 4K without touching a row sends a plain 4K request`() =
        runTest {
            val repository = advanced()
            val vm = viewModel(repository)
            vm.setUse4k(true)
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            val submission = repository.submissions.single()
            assertTrue(submission.is4k)
            assertNull(submission.destination)
        }

    @Test
    fun `profiles that fail to load still allow a plain request, and read again`() {
        val repository = advanced().apply { failChoices = IOException("down") }
        val vm = viewModel(repository)

        assertEquals(ChoicesState.Failed, vm.ready().pick?.choices)
        assertTrue(vm.ready().canSubmit)
        assertNull(vm.ready().destination)

        repository.failChoices = null
        vm.retry(RequestRead.Choices)

        assertEquals(ChoicesState.Ready(CHOICES), vm.ready().pick?.choices)
        assertTrue(vm.ready().canSubmit)
    }

    @Test
    fun `a user whose version has no server is not offered the destination rows`() {
        val repository = FakeRepository(ADVANCED, servers = listOf(STANDARD))
        val vm = viewModel(repository)

        vm.setUse4k(true)

        assertFalse(vm.ready().choosesDestination)
    }

    @Test
    fun `a refusal is a dialog with the server's words and unlocks the sheet`() {
        val vm = viewModel(advanced().apply { failSubmit = refusal(403, "Request quota exceeded") })

        vm.submit()

        val state = vm.ready()
        assertFalse(state.submitting)
        assertEquals(RequestProblem(RequestProblemCause.Quota, "Request quota exceeded"), state.problem)
        assertNull(state.failure)
        vm.dismissProblem()
        assertNull(vm.ready().problem)
    }

    @Test
    fun `a server answer with its own reason goes to the message dialog, not the red footer`() {
        val vm = viewModel(advanced().apply { failSubmit = refusal(400, "Quality profile not found") })

        vm.submit()

        val state = vm.ready()
        assertEquals(RequestProblem(RequestProblemCause.Other, "Quality profile not found"), state.problem)
        assertNull(state.failure)
        assertFalse(state.footerFailed)
        assertFalse(state.submitting)
    }

    @Test
    fun `a server answer with no reason has nothing to act on, so it turns the footer red`() {
        val empty = HttpException(Response.error<Any>(500, "".toResponseBody("application/json".toMediaType())))
        val vm = viewModel(advanced().apply { failSubmit = empty })

        vm.submit()

        val state = vm.ready()
        assertEquals(RequestProblemCause.Other, state.failure?.cause)
        assertTrue(state.footerFailed)
        assertNull(state.problem)
    }

    @Test
    fun `an unreachable server turns the footer red for four seconds, then back`() =
        runTest {
            val vm = viewModel(FakeRepository(PLAIN, failSubmit = java.net.UnknownHostException("x")))

            vm.submit()

            assertEquals(RequestProblemCause.Unreachable, vm.ready().failure?.cause)
            assertTrue(vm.ready().footerFailed)
            assertNull(vm.ready().problem)

            advanceTimeBy(FOOTER_RED_MILLIS + 1)
            main.dispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.ready().footerFailed)
        }

    @Test
    fun `an old footer timer cannot cut a newer identical failure's red window short`() =
        runTest {
            val vm = viewModel(FakeRepository(PLAIN, failSubmit = java.net.UnknownHostException("x")))
            vm.submit()
            advanceTimeBy(FOOTER_RED_MILLIS / 2)
            vm.openFailure()
            vm.dismissFailure()
            vm.submit()
            assertTrue(vm.ready().footerFailed)

            advanceTimeBy(FOOTER_RED_MILLIS / 2 + 1)
            main.dispatcher.scheduler.runCurrent()

            assertTrue(vm.ready().footerFailed)

            advanceTimeBy(FOOTER_RED_MILLIS)
            main.dispatcher.scheduler.runCurrent()

            assertFalse(vm.ready().footerFailed)
        }

    @Test
    fun `a tap on the red footer opens the dialog, and its Retry sends again`() {
        val repository = FakeRepository(PLAIN, failSubmit = java.net.UnknownHostException("x"))
        val vm = viewModel(repository)
        vm.submit()

        vm.openFailure()
        assertTrue(vm.ready().failureDialog)

        repository.failSubmit = null
        vm.dismissFailure()
        vm.submit()

        assertEquals(2, repository.submissions.size)
        assertNull(vm.ready().failure)
    }

    @Test
    fun `an opening of the failure with nothing failed does nothing`() {
        val vm = viewModel(FakeRepository(PLAIN))

        vm.openFailure()

        assertFalse(vm.ready().failureDialog)
    }

    @Test
    fun `a title whose standard version is requested defaults to 4K and cannot flip back`() {
        val vm = viewModel(advanced(), TARGET.copy(standardOpen = false))

        assertTrue(vm.ready().use4k)
        assertEquals(2, vm.ready().pick?.serverId)
        assertFalse(vm.ready().canToggle4k)
        vm.setUse4k(false)
        assertTrue(vm.ready().use4k)
    }

    @Test
    fun `a title whose 4K version is requested offers the standard one only`() {
        val vm = viewModel(advanced(), TARGET.copy(fourKOpen = false))

        assertFalse(vm.ready().use4k)
        assertFalse(vm.ready().canToggle4k)
    }

    @Test
    fun `an already requested answer and a nothing to request answer both close the sheet as done`() =
        runTest {
            for (answer in listOf(RequestResult.AlreadyRequested, RequestResult.NothingToRequest)) {
                val vm = viewModel(advanced().apply { result = answer })
                val event = awaitEvent(vm.events)

                vm.submit()

                assertEquals(RequestSheetEvent.Done(answer), event.await())
                assertNull(vm.ready().problem)
            }
        }

    @Test
    fun `a sent request leaves the sheet ready to open again`() =
        runTest {
            val vm = viewModel(advanced())
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            assertFalse(vm.ready().submitting)
            assertTrue(vm.ready().canSubmit)
        }
}

private const val FOOTER_RED_MILLIS = 4_000L
