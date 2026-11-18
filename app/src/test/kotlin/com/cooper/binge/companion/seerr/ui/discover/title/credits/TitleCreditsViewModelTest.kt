package com.cooper.binge.companion.seerr.ui.discover.title.credits

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.title.CreditsDepartment
import com.cooper.binge.companion.seerr.discover.title.TitleCredits
import com.cooper.binge.companion.seerr.discover.title.TitleCreditsRepository
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

private val CREDITS =
    TitleCredits(
        cast = listOf(TitlePerson(1, "A", "Neo", null)),
        departments = listOf(CreditsDepartment("Directing", listOf(TitlePerson(2, "B", "Director", null)))),
    )

private class FakeCredits(
    var failure: Throwable? = null,
) : TitleCreditsRepository {
    override suspend fun credits(
        kind: TitleKind,
        tmdbId: Int,
    ): TitleCredits = failure?.let { throw it } ?: CREDITS
}

class TitleCreditsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `the credits load`() {
        val vm = TitleCreditsViewModel(FakeCredits(), TitleKind.Movie, 603)

        assertEquals(TitleCreditsUiState.Ready(CREDITS), vm.uiState.value)
    }

    @Test
    fun `a failed load is an error that retries`() {
        val fake = FakeCredits(IOException("down"))
        val vm = TitleCreditsViewModel(fake, TitleKind.Movie, 603)
        assertTrue(vm.uiState.value is TitleCreditsUiState.Error)

        fake.failure = null
        vm.load()

        assertEquals(TitleCreditsUiState.Ready(CREDITS), vm.uiState.value)
    }
}
