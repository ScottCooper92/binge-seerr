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
import com.cooper.binge.companion.seerr.discover.request.SeasonChoice
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class SeasonsRepository : RequestSheetRepository {
    val submissions = mutableListOf<RequestSubmission>()

    var quota: RequestQuota? = null

    override suspend fun access(kind: TitleKind) = RequestAccess(may4k = true, mayChooseDestination = false)

    override suspend fun quota(kind: TitleKind): RequestQuota? = quota

    override suspend fun servers(kind: TitleKind) = emptyList<RequestServer>()

    override suspend fun choices(
        kind: TitleKind,
        serverId: Int,
    ) = RequestChoices(emptyList(), emptyList())

    override suspend fun submitAll(submissions: List<RequestSubmission>) = RequestAllResult(submissions.size, 0, null)

    override suspend fun submit(submission: RequestSubmission): RequestResult {
        submissions += submission
        return RequestResult.Created
    }
}

private fun season(
    number: Int,
    availability: TitleAvailability = TitleAvailability.None,
) = SeasonChoice(number, "Season $number", 8, availability)

class RequestSheetSeasonsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = SeasonsRepository()

    private val target =
        RequestSheetTarget(
            kind = TitleKind.Series,
            tmdbId = 1399,
            title = "A Show",
            seasonChoices =
                SeasonChoices(
                    standard = listOf(season(1, TitleAvailability.Available), season(2), season(3)),
                    fourK = listOf(season(1), season(2), season(3, TitleAvailability.Pending)),
                ),
        )

    private fun viewModel(
        target: RequestSheetTarget = this.target,
        quota: RequestQuota? = null,
    ): RequestSheetViewModel {
        repository.quota = quota
        return RequestSheetViewModel(repository, target)
    }

    private fun RequestSheetViewModel.ready() = uiState.value as RequestSheetUiState.Ready

    @Test
    fun `a request needs a season picked and sends the picked ones`() {
        val vm = viewModel()
        assertFalse(vm.ready().canSubmit)

        vm.toggleSeasons(3)
        vm.toggleSeasons(2)
        assertTrue(vm.ready().canSubmit)
        vm.submit()

        assertEquals(listOf(2, 3), repository.submissions.single().seasons)
    }

    @Test
    fun `a covered season cannot be picked`() {
        val vm = viewModel()

        vm.toggleSeasons(1)

        assertFalse(vm.ready().canSubmit)
    }

    @Test
    fun `all seasons picks every open one`() {
        val vm = viewModel()

        vm.toggleSeasons(null)
        vm.submit()

        assertEquals(listOf(2, 3), repository.submissions.single().seasons)
    }

    @Test
    fun `switching to 4K lists that version's seasons and clears the picks`() {
        val vm = viewModel()
        vm.toggleSeasons(2)

        vm.setUse4k(true)

        assertTrue(vm.ready().use4k)
        assertTrue(vm.ready().pickedSeasons.isEmpty())
        assertEquals(
            listOf(1, 2),
            vm
                .ready()
                .seasons
                ?.open
                ?.map { it.number },
        )
    }

    @Test
    fun `with partial requests off the request is every open season with no pick`() {
        val vm = viewModel(target.copy(partialRequests = false))

        assertTrue(vm.ready().canSubmit)
        vm.submit()

        assertEquals(listOf(2, 3), repository.submissions.single().seasons)
    }

    @Test
    fun `a film has no season step and sends no seasons`() {
        val vm = viewModel(RequestSheetTarget(TitleKind.Movie, 603, "The Matrix"))

        assertTrue(vm.ready().seasons == null)
        assertTrue(vm.ready().canSubmit)
        vm.submit()

        assertTrue(
            repository.submissions
                .single()
                .seasons
                .isEmpty(),
        )
    }

    @Test
    fun `a quota with one season left lets one season through and says so`() {
        val vm = viewModel(quota = RequestQuota(limit = 10, remaining = 1, days = 7))

        vm.toggleSeasons(2)
        vm.toggleSeasons(3)

        assertEquals(setOf(2), vm.ready().pickedSeasons)
        assertEquals(1, vm.ready().seasons?.limit)
    }
}
