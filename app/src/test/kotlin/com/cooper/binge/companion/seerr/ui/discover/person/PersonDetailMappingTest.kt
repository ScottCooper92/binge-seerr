package com.cooper.binge.companion.seerr.ui.discover.person

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.ErrorKind
import com.binge.designsystem.template.CreditsState
import com.binge.designsystem.template.PersonDetailState
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.person.PersonCredit
import com.cooper.binge.companion.seerr.discover.person.PersonCredits
import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.seerr.SeerrError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class PersonDetailMappingTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private val person =
        PersonDetails(
            personId = 1,
            name = "Riley Director",
            knownFor = "Directing",
            biography = "A bio.",
            birthday = "1970-01-02",
            deathday = null,
            placeOfBirth = "Demo City",
            alsoKnownAs = emptyList(),
            gender = null,
            popularity = null,
            imdbId = null,
            homepage = null,
            photoUrl = null,
            webUrl = "https://seerr.example/person/1",
        )

    private val credit =
        PersonCredit(TitleKind.Movie, 603, "The Matrix", 8.2f, "https://img/p.jpg", "Neo", TitleAvailability.Available)

    private fun ready(credits: PersonCreditsState) = PersonPageUiState.Ready(person, credits)

    private fun content(state: PersonPageUiState) = (state.toDetailState(resources, Locale.US) as PersonDetailState.Content).person

    @Test
    fun `loading and failure map to the template's states with the failure's kind`() {
        assertTrue(PersonPageUiState.Loading.toDetailState(resources, Locale.US) is PersonDetailState.Loading)

        val failed = PersonPageUiState.Error(SeerrError.Unreachable).toDetailState(resources, Locale.US) as PersonDetailState.Failed
        assertEquals(ErrorKind.Network, failed.kind)
        assertTrue(failed.retryable)
    }

    @Test
    fun `a person carries their name eyebrow biography and only the facts the server gave`() {
        val detail = content(ready(PersonCreditsState.Loading))

        assertEquals("Riley Director", detail.name)
        assertEquals("Directing", detail.eyebrow)
        assertEquals("A bio.", detail.biography)
        assertEquals(2, detail.facts.size)
        assertEquals("Demo City", detail.facts.last().value)
    }

    @Test
    fun `credits are two rails with the role as the subtitle and the title's status on the card`() {
        val detail = content(ready(PersonCreditsState.Ready(PersonCredits(cast = listOf(credit), crew = emptyList()))))

        val sections = (detail.credits as CreditsState.Ready).sections
        assertEquals(listOf("Known for", "Key crew"), sections.map { it.title })
        val item = sections.first().items.single()
        assertEquals(credit.key, item.key)
        assertEquals("Neo", item.subtitle)
        assertEquals("Available", item.status?.label)
        assertTrue(sections.last().items.isEmpty())
        assertTrue(sections.none { it.hasMore })
    }

    @Test
    fun `a credit with nothing held has no status and a failed credits read keeps its own message`() {
        val none = credit.copy(availability = TitleAvailability.None)
        val credits = content(ready(PersonCreditsState.Ready(PersonCredits(listOf(none), emptyList())))).credits
        val sections = (credits as CreditsState.Ready).sections
        assertNull(
            sections
                .first()
                .items
                .single()
                .status,
        )

        assertTrue(content(ready(PersonCreditsState.Error(SeerrError.Unreachable))).credits is CreditsState.Failed)
    }
}
