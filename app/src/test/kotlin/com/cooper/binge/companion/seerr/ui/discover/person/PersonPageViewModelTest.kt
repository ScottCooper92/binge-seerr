package com.cooper.binge.companion.seerr.ui.discover.person

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.person.PersonCredit
import com.cooper.binge.companion.seerr.discover.person.PersonCredits
import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.discover.person.PersonRepository
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

internal fun personFixture() =
    PersonDetails(
        personId = 5,
        name = "Sam Example",
        knownFor = "Acting",
        biography = "A biography.",
        birthday = "1980-02-14",
        deathday = null,
        placeOfBirth = "Demo City",
        alsoKnownAs = listOf("S."),
        photoUrl = null,
    )

internal fun creditFixture(
    kind: TitleKind,
    id: Int,
    title: String = "Title $id",
) = PersonCredit(kind, id, title, "2020-01-01", null, "Role", TitleAvailability.None)

private class FakePeople(
    var person: PersonDetails = personFixture(),
    var credits: PersonCredits =
        PersonCredits(
            cast = listOf(creditFixture(TitleKind.Movie, 1), creditFixture(TitleKind.Series, 2)),
            crew = listOf(creditFixture(TitleKind.Movie, 3)),
        ),
    var failDetails: Throwable? = null,
    var failCredits: Throwable? = null,
) : PersonRepository {
    override suspend fun details(personId: Int): PersonDetails = failDetails?.let { throw it } ?: person

    override suspend fun credits(personId: Int): PersonCredits = failCredits?.let { throw it } ?: credits
}

class PersonPageViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun PersonPageViewModel.ready() = uiState.value as PersonPageUiState.Ready

    @Test
    fun `the page loads the profile and the credits`() {
        val vm = PersonPageViewModel(FakePeople(), 5)

        assertEquals("Sam Example", vm.ready().person.name)
        assertEquals(3, vm.ready().visible?.let { it.cast.size + it.crew.size })
    }

    @Test
    fun `a profile that fails to load is an error that retries`() {
        val repository = FakePeople(failDetails = IOException("down"))
        val vm = PersonPageViewModel(repository, 5)
        assertTrue(vm.uiState.value is PersonPageUiState.Error)

        repository.failDetails = null
        vm.load()

        assertTrue(vm.uiState.value is PersonPageUiState.Ready)
    }

    @Test
    fun `credits that fail leave the profile and retry alone`() {
        val repository = FakePeople(failCredits = IOException("down"))
        val vm = PersonPageViewModel(repository, 5)

        assertEquals("Sam Example", vm.ready().person.name)
        assertTrue(vm.ready().credits is PersonCreditsState.Error)
        assertNull(vm.ready().visible)

        repository.failCredits = null
        vm.retryCredits()

        assertTrue(vm.ready().credits is PersonCreditsState.Ready)
    }

    @Test
    fun `the chips narrow both lists to films or series`() {
        val vm = PersonPageViewModel(FakePeople(), 5)

        vm.setFilter(CreditsFilter.Movies)
        assertEquals(
            listOf(1),
            vm
                .ready()
                .visible
                ?.cast
                ?.map { it.tmdbId },
        )
        assertEquals(
            listOf(3),
            vm
                .ready()
                .visible
                ?.crew
                ?.map { it.tmdbId },
        )

        vm.setFilter(CreditsFilter.Series)
        assertEquals(
            listOf(2),
            vm
                .ready()
                .visible
                ?.cast
                ?.map { it.tmdbId },
        )
        assertTrue(
            vm
                .ready()
                .visible
                ?.crew
                ?.isEmpty() == true,
        )

        vm.setFilter(CreditsFilter.All)
        assertEquals(
            2,
            vm
                .ready()
                .visible
                ?.cast
                ?.size,
        )
    }

    @Test
    fun `a person with no credits is ready with empty lists`() {
        val vm = PersonPageViewModel(FakePeople(credits = PersonCredits(emptyList(), emptyList())), 5)

        assertTrue(vm.ready().visible?.isEmpty == true)
    }
}
