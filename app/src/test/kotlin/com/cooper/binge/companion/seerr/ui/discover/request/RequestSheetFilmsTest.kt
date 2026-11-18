package com.cooper.binge.companion.seerr.ui.discover.request

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestAllResult
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.discover.request.RequestSheetRepository
import com.cooper.binge.companion.seerr.discover.request.RequestSubmission
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

private class FilmsRepository(
    var quota: RequestQuota? = null,
    var answer: RequestAllResult? = null,
) : RequestSheetRepository {
    val sent = mutableListOf<RequestSubmission>()

    override suspend fun access(kind: TitleKind) = RequestAccess(may4k = true, mayChooseDestination = false)

    override suspend fun quota(kind: TitleKind): RequestQuota? = quota

    override suspend fun servers(kind: TitleKind) = emptyList<RequestServer>()

    override suspend fun choices(
        kind: TitleKind,
        serverId: Int,
    ) = RequestChoices(emptyList(), emptyList())

    override suspend fun submit(submission: RequestSubmission): RequestResult = RequestResult.Created

    override suspend fun submitAll(submissions: List<RequestSubmission>): RequestAllResult {
        sent += submissions
        return answer ?: RequestAllResult(submissions.size, 0, null)
    }
}

private fun film(
    id: Int,
    availability: TitleAvailability = TitleAvailability.None,
) = RequestFilm(id, "Film $id", "${2000 + id}", availability)

private fun collection(vararg films: RequestFilm) =
    RequestSheetTarget(
        kind = TitleKind.Movie,
        tmdbId = 2344,
        title = "The Collection",
        fourKOpen = false,
        films = films.toList(),
    )

class RequestSheetFilmsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun RequestSheetViewModel.ready() = uiState.value as RequestSheetUiState.Ready

    @Test
    fun `every open film is ticked at first when the quota covers them`() {
        val vm =
            RequestSheetViewModel(
                FilmsRepository(RequestQuota(10, 5, 7)),
                collection(film(1), film(2), film(3, TitleAvailability.Available)),
            )

        assertEquals(listOf(1, 2), vm.ready().films?.requestIds)
    }

    @Test
    fun `none is ticked at first when the quota cannot take every open film`() {
        val vm = RequestSheetViewModel(FilmsRepository(RequestQuota(10, 1, 7)), collection(film(1), film(2)))

        assertEquals(emptyList<Int>(), vm.ready().films?.requestIds)
        assertFalse(vm.ready().canSubmit)
    }

    @Test
    fun `ticking and clearing films changes what is sent`() =
        runTest {
            val repository = FilmsRepository()
            val vm = RequestSheetViewModel(repository, collection(film(1), film(2), film(3)))

            vm.toggleFilms(2)
            assertEquals(listOf(1, 3), vm.ready().films?.requestIds)
            vm.toggleFilms(null)
            assertEquals(listOf(1, 2, 3), vm.ready().films?.requestIds)

            val event = awaitEvent(vm.events)
            vm.toggleFilms(1)
            vm.submit()

            assertEquals(RequestSheetEvent.Done(RequestResult.Created), event.await())
            assertEquals(listOf(2, 3), repository.sent.map { it.tmdbId })
            assertTrue(repository.sent.all { it.kind == TitleKind.Movie && !it.is4k && it.seasons.isEmpty() })
        }

    @Test
    fun `a send where some films were refused says so and still closes`() =
        runTest {
            val repository = FilmsRepository(answer = RequestAllResult(created = 1, alreadyThere = 0, failure = IOException("down")))
            val vm = RequestSheetViewModel(repository, collection(film(1), film(2)))

            val event = awaitEvent(vm.events)
            vm.submit()

            assertEquals(RequestSheetEvent.Done(RequestResult.PartiallyCreated), event.await())
        }

    @Test
    fun `a send where every film was refused stays open with the reason`() {
        val repository = FilmsRepository(answer = RequestAllResult(created = 0, alreadyThere = 0, failure = IOException("down")))
        val vm = RequestSheetViewModel(repository, collection(film(1), film(2)))

        vm.submit()

        assertTrue(vm.ready().failure != null)
        assertFalse(vm.ready().submitting)
    }

    @Test
    fun `a send where every film was already there says so`() =
        runTest {
            val repository = FilmsRepository(answer = RequestAllResult(created = 0, alreadyThere = 2, failure = null))
            val vm = RequestSheetViewModel(repository, collection(film(1), film(2)))

            val event = awaitEvent(vm.events)
            vm.submit()

            assertEquals(RequestSheetEvent.Done(RequestResult.AlreadyRequested), event.await())
        }
}
