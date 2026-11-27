package com.cooper.binge.companion.seerr.ui.discover.request

import com.cooper.binge.companion.seerr.discover.TitleAvailability
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
import com.cooper.binge.companion.seerr.discover.request.RequestUser
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import com.cooper.homelab.core.testing.RecordingAnalytics
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
private val SERIES = RequestSheetTarget(TitleKind.Series, tmdbId = 1399, title = "Frieren")
private val STANDARD = RequestServer(1, "Radarr", is4k = false, isDefault = true, profileId = 4, rootFolder = "/movies")
private val UHD = RequestServer(2, "Radarr 4K", is4k = true, isDefault = false, profileId = 7, rootFolder = "/uhd")
private val CHOICES = RequestChoices(listOf(RequestChoice(4, "HD"), RequestChoice(5, "Any")), listOf("/movies", "/more"))
private val PLAIN = RequestAccess(may4k = false, mayChooseDestination = false)
private val ADVANCED = RequestAccess(may4k = true, mayChooseDestination = true)
private val OTHERS = RequestAccess(may4k = true, mayChooseDestination = true, mayRequestForOthers = true, viewerId = 9)
private val ADA = RequestUser(9, "Ada")
private val GRACE = RequestUser(12, "Grace")

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
    var choices: RequestChoices = CHOICES
    var users: List<RequestUser> = emptyList()
    var failUsers: Throwable? = null

    var anime = false
    var failAnime: Throwable? = null

    override suspend fun isAnime(tmdbId: Int): Boolean = failAnime?.let { throw it } ?: anime

    override suspend fun access(kind: TitleKind): RequestAccess = failAccess?.let { throw it } ?: access

    override suspend fun quota(kind: TitleKind): RequestQuota? = failQuota?.let { throw it } ?: quota

    override suspend fun servers(kind: TitleKind): List<RequestServer> = failServers?.let { throw it } ?: servers

    override suspend fun users(): List<RequestUser> = failUsers?.let { throw it } ?: users

    override suspend fun choices(
        kind: TitleKind,
        serverId: Int,
    ): RequestChoices = failChoices?.let { throw it } ?: choices

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

    private fun forOthers(quota: RequestQuota? = RequestQuota(10, 2, 7)) =
        FakeRepository(OTHERS, quota, listOf(STANDARD, UHD)).apply { users = listOf(ADA, GRACE) }

    @Test
    fun `a user who may not request for others is offered no users`() {
        val vm = viewModel(advanced())

        assertEquals(UsersState.None, vm.ready().users)
    }

    @Test
    fun `the users are read for one who may request for others`() {
        val vm = viewModel(forOthers())

        assertEquals(UsersState.Ready(listOf(ADA, GRACE)), vm.ready().users)
        assertNull(vm.ready().requestFor)
    }

    @Test
    fun `a request made for another user names them`() =
        runTest {
            val repository = forOthers()
            val vm = viewModel(repository)
            vm.selectRequestFor(12)
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            assertEquals(12, repository.submissions.single().userId)
        }

    @Test
    fun `choosing oneself again names no one`() =
        runTest {
            val repository = forOthers()
            val vm = viewModel(repository)
            vm.selectRequestFor(12)
            vm.selectRequestFor(9)
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            assertNull(repository.submissions.single().userId)
        }

    @Test
    fun `choosing oneself again works when the users read does not hold the sender`() =
        runTest {
            val repository = forOthers().apply { users = listOf(GRACE) }
            val vm = viewModel(repository)
            vm.selectRequestFor(12)
            vm.selectRequestFor(9)
            val event = awaitEvent(vm.events)

            assertNull(vm.ready().requestFor)
            vm.submit()
            event.await()

            assertNull(repository.submissions.single().userId)
        }

    @Test
    fun `an id the users read does not hold changes nothing`() {
        val vm = viewModel(forOthers())

        vm.selectRequestFor(404)

        assertNull(vm.ready().requestFor)
    }

    @Test
    fun `the sender's spent quota does not stop a request made for someone else`() {
        val vm = viewModel(forOthers(quota = RequestQuota(10, 0, 7)))
        assertFalse(vm.ready().canSubmit)

        vm.selectRequestFor(12)

        assertTrue(vm.ready().canSubmit)
    }

    @Test
    fun `a failed users read says so and a retry reads them again`() {
        val repository = forOthers().apply { failUsers = IOException("down") }
        val vm = viewModel(repository)
        assertEquals(UsersState.Failed, vm.ready().users)

        repository.failUsers = null
        vm.retry(RequestRead.Users)

        assertEquals(UsersState.Ready(listOf(ADA, GRACE)), vm.ready().users)
    }

    private val tagged =
        CHOICES.copy(tags = listOf(RequestChoice(1, "family"), RequestChoice(2, "anime")), defaultTagIds = setOf(1))

    @Test
    fun `the server's default tags are shown before the user touches them and left to the server`() =
        runTest {
            val repository = advanced().apply { choices = tagged }
            val vm = viewModel(repository)
            val event = awaitEvent(vm.events)

            assertEquals(setOf(1), vm.ready().pick?.shownTagIds())
            vm.selectProfile(5)
            vm.submit()
            event.await()

            val destination = repository.submissions.single().destination
            assertNull(destination?.tags)
        }

    @Test
    fun `a tag put on joins the defaults and goes with the destination`() =
        runTest {
            val repository = advanced().apply { choices = tagged }
            val vm = viewModel(repository)
            vm.toggleTag(2)
            val event = awaitEvent(vm.events)

            assertEquals(setOf(1, 2), vm.ready().pick?.shownTagIds())
            vm.submit()
            event.await()

            val destination = repository.submissions.single().destination
            assertEquals(listOf(1, 2), destination?.tags)
        }

    @Test
    fun `taking off the last default sends an empty list, not the server's choice`() =
        runTest {
            val repository = advanced().apply { choices = tagged }
            val vm = viewModel(repository)
            vm.toggleTag(1)
            val event = awaitEvent(vm.events)

            vm.submit()
            event.await()

            val destination = repository.submissions.single().destination
            assertEquals(emptyList<Int>(), destination?.tags)
        }

    private val sonarr =
        STANDARD.copy(name = "Sonarr", profileId = 4, rootFolder = "/tv", animeProfileId = 5, animeRootFolder = "/anime")
    private val animeChoices =
        CHOICES.copy(
            rootFolders = listOf("/tv", "/anime"),
            tags = listOf(RequestChoice(1, "family"), RequestChoice(2, "anime")),
            defaultTagIds = setOf(1),
            animeProfileId = 5,
            animeTagIds = setOf(2),
        )

    private fun sonarrRepository(anime: Boolean) =
        FakeRepository(ADVANCED, null, listOf(sonarr)).apply {
            choices = animeChoices
            this.anime = anime
        }

    @Test
    fun `an anime series starts on the anime profile and folder and shows the anime tags`() {
        val vm = viewModel(sonarrRepository(anime = true), SERIES)

        val pick = vm.ready().pick
        assertEquals(5, pick?.profileId)
        assertEquals("/anime", pick?.rootFolder)
        assertEquals(setOf(2), pick?.shownTagIds())
    }

    @Test
    fun `a series that is not anime keeps the regular defaults`() {
        val vm = viewModel(sonarrRepository(anime = false), SERIES)

        val pick = vm.ready().pick
        assertEquals(4, pick?.profileId)
        assertEquals("/tv", pick?.rootFolder)
        assertEquals(setOf(1), pick?.shownTagIds())
    }

    @Test
    fun `a film never asks whether it is anime`() {
        val vm = viewModel(sonarrRepository(anime = true), TARGET)

        assertFalse(vm.ready().anime)
        assertEquals(setOf(1), vm.ready().pick?.shownTagIds())
    }

    @Test
    fun `toggling a tag on an anime series keeps its anime defaults and sends them`() =
        runTest {
            val repository = sonarrRepository(anime = true)
            val vm = viewModel(repository, SERIES)
            vm.toggleTag(1)
            val event = awaitEvent(vm.events)

            assertEquals(setOf(1, 2), vm.ready().pick?.shownTagIds())
            vm.submit()
            event.await()

            val destination = repository.submissions.single().destination
            assertEquals(listOf(2, 1), destination?.tags)
            assertEquals(5, destination?.profileId)
            assertEquals("/anime", destination?.rootFolder)
        }

    @Test
    fun `an anime series on an instance with no anime profile falls back to the regular one`() {
        val repository = sonarrRepository(anime = true).apply { servers = listOf(sonarr.copy(animeProfileId = null)) }
        val vm = viewModel(repository, SERIES)

        assertEquals(4, vm.ready().pick?.profileId)
    }

    @Test
    fun `a failed anime read leaves the regular defaults`() {
        val repository = sonarrRepository(anime = true).apply { failAnime = IOException("down") }
        val vm = viewModel(repository, SERIES)

        assertEquals(4, vm.ready().pick?.profileId)
    }

    @Test
    fun `switching to 4K drops the tags chosen for the standard server`() =
        runTest {
            val repository = advanced().apply { choices = tagged }
            val vm = viewModel(repository)
            vm.toggleTag(2)

            vm.setUse4k(true)

            assertNull(vm.ready().pick?.tagIds)
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
    fun `a sent request records request_submitted with its kind and action`() {
        val analytics = RecordingAnalytics()
        val vm = RequestSheetViewModel(FakeRepository(PLAIN), TARGET, analytics)

        vm.submit()

        assertEquals(
            listOf(
                AnalyticsEvents.REQUEST_SUBMITTED to
                    mapOf(AnalyticsEvents.PARAM_TYPE to "movie", AnalyticsEvents.PARAM_ACTION to "title"),
            ),
            analytics.events,
        )
    }

    @Test
    fun `a failed send records nothing`() {
        val analytics = RecordingAnalytics()
        val vm = RequestSheetViewModel(FakeRepository(PLAIN, failSubmit = IOException("down")), TARGET, analytics)

        vm.submit()

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `a send that created nothing records nothing`() {
        val analytics = RecordingAnalytics()
        val vm = RequestSheetViewModel(FakeRepository(PLAIN, result = RequestResult.AlreadyRequested), TARGET, analytics)

        vm.submit()

        assertTrue(analytics.events.isEmpty())
    }

    private fun reported(analytics: RecordingAnalytics) = analytics.events.filter { it.first == AnalyticsEvents.REQUEST_OPERATION_FAILED }

    @Test
    fun `a 500 on submit is reported as request_submit`() {
        val analytics = RecordingAnalytics()
        val vm = RequestSheetViewModel(FakeRepository(PLAIN, failSubmit = refusal(500, "boom")), TARGET, analytics)

        vm.submit()

        val event = reported(analytics).single().second
        assertEquals("request_submit", event[AnalyticsEvents.PARAM_OPERATION])
        assertEquals("500", event[AnalyticsEvents.PARAM_HTTP_STATUS])
    }

    @Test
    fun `a quota refusal and a 403 on submit are not reported`() {
        val analytics = RecordingAnalytics()
        RequestSheetViewModel(FakeRepository(PLAIN, failSubmit = refusal(403, "Request quota exceeded")), TARGET, analytics).submit()
        RequestSheetViewModel(FakeRepository(PLAIN, failSubmit = refusal(403, "Forbidden")), TARGET, analytics).submit()

        assertTrue(reported(analytics).isEmpty())
    }

    @Test
    fun `a request for several films that created nothing reports the failure, and one that created some does not`() {
        val analytics = RecordingAnalytics()
        val film = RequestFilm(1, "A", "2001", TitleAvailability.None)
        val films = RequestSheetTarget(TitleKind.Movie, 2344, "The Collection", films = listOf(film))

        val none = FakeRepository(PLAIN).apply { allResult = RequestAllResult(0, 0, refusal(500, "boom")) }
        RequestSheetViewModel(none, films, analytics).submit()
        assertEquals("request_submit", reported(analytics).single().second[AnalyticsEvents.PARAM_OPERATION])

        val some = FakeRepository(PLAIN).apply { allResult = RequestAllResult(1, 0, refusal(500, "boom")) }
        RequestSheetViewModel(some, films, analytics).submit()
        assertEquals(1, reported(analytics).size)
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
