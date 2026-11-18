package com.cooper.binge.companion.seerr.ui.discover.request

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestOptions
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.discover.request.RequestSheetRepository
import com.cooper.binge.companion.seerr.discover.request.RequestSubmission
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
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

private class FakeRepository(
    var options: RequestOptions,
    var failOptions: Throwable? = null,
    var failSubmit: Throwable? = null,
    var result: RequestResult = RequestResult.Created,
) : RequestSheetRepository {
    val submissions = mutableListOf<RequestSubmission>()

    override suspend fun options(kind: TitleKind): RequestOptions = failOptions?.let { throw it } ?: options

    override suspend fun choices(
        kind: TitleKind,
        serverId: Int,
    ) = CHOICES

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

    private fun viewModel(repository: FakeRepository) = RequestSheetViewModel(repository, TARGET)

    private fun RequestSheetViewModel.ready() = uiState.value as RequestSheetUiState.Ready

    private val plain = RequestOptions(may4k = false, servers = emptyList(), quota = null)
    private val advanced = RequestOptions(may4k = true, servers = listOf(STANDARD, UHD), quota = RequestQuota(10, 2, 7))

    @Test
    fun `a failed load is an error screen that retries`() {
        val repository = FakeRepository(plain, failOptions = IOException("down"))
        val vm = viewModel(repository)

        assertTrue(vm.uiState.value is RequestSheetUiState.Error)

        repository.failOptions = null
        vm.load()

        assertTrue(vm.uiState.value is RequestSheetUiState.Ready)
    }

    @Test
    fun `a user with no destination to choose has no pick and sends a plain request`() =
        runTest {
            val repository = FakeRepository(plain)
            val vm = viewModel(repository)
            assertNull(vm.ready().pick)

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
        val vm = viewModel(FakeRepository(advanced))

        val pick = vm.ready().pick!!
        assertEquals(1, pick.serverId)
        assertEquals(CHOICES, pick.choices)
        assertEquals(4, pick.profileId)
        assertEquals("/movies", pick.rootFolder)
    }

    @Test
    fun `the 4K switch moves to a 4K server and back`() {
        val vm = viewModel(FakeRepository(advanced))

        vm.setUse4k(true)
        assertEquals(2, vm.ready().pick?.serverId)
        assertEquals(listOf(UHD), vm.ready().servers)

        vm.setUse4k(false)
        assertEquals(1, vm.ready().pick?.serverId)
    }

    @Test
    fun `a pick that the new server does not offer falls back to its first`() {
        val vm = viewModel(FakeRepository(advanced))

        vm.selectProfile(5)
        vm.selectRootFolder("/more")
        assertEquals(5, vm.ready().pick?.profileId)
        assertEquals("/more", vm.ready().pick?.rootFolder)

        vm.setUse4k(true)
        assertEquals(4, vm.ready().pick?.profileId)
        assertEquals("/movies", vm.ready().pick?.rootFolder)
    }

    @Test
    fun `a submit carries the chosen destination and 4K`() =
        runTest {
            val repository = FakeRepository(advanced)
            val vm = viewModel(repository)
            vm.setUse4k(true)

            val event = awaitEvent(vm.events)
            vm.submit()
            event.await()

            val sent = repository.submissions.single()
            assertTrue(sent.is4k)
            assertEquals(2, sent.destination?.serverId)
        }

    @Test
    fun `a spent quota holds the submit`() {
        val spent = advanced.copy(quota = RequestQuota(10, 0, 7))
        val repository = FakeRepository(spent)
        val vm = viewModel(repository)

        assertFalse(vm.ready().canSubmit)
        vm.submit()

        assertTrue(repository.submissions.isEmpty())
    }

    @Test
    fun `a refusal is a dialog with the server's words and unlocks the sheet`() {
        val repository = FakeRepository(advanced, failSubmit = refusal(403, "Request quota exceeded"))
        val vm = viewModel(repository)

        vm.submit()

        val state = vm.ready()
        assertFalse(state.submitting)
        assertEquals(RequestProblem(RequestProblemCause.Quota, "Request quota exceeded"), state.problem)
        vm.dismissProblem()
        assertNull(vm.ready().problem)
    }

    @Test
    fun `an unreachable server is a problem the user can retry`() {
        val repository = FakeRepository(plain, failSubmit = java.net.UnknownHostException("x"))
        val vm = viewModel(repository)

        vm.submit()

        assertEquals(RequestProblemCause.Unreachable, vm.ready().problem?.cause)
    }
}
