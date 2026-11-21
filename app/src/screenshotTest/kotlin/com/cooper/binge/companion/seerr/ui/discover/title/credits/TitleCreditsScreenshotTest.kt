package com.cooper.binge.companion.seerr.ui.discover.title.credits

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.title.CreditsDepartment
import com.cooper.binge.companion.seerr.discover.title.TitleCredits
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError

private fun person(
    id: Int,
    name: String,
    role: String?,
) = TitlePerson(id, name, role, null)

private fun sampleCredits() =
    TitleCredits(
        cast = listOf(person(1, "Keanu Reeves", "Neo"), person(2, "Carrie-Anne Moss", "Trinity"), person(3, "Laurence Fishburne", null)),
        departments =
            listOf(
                CreditsDepartment("Directing", listOf(person(10, "Lana Wachowski", "Director"), person(11, "Lilly Wachowski", "Director"))),
                CreditsDepartment("Writing", listOf(person(10, "Lana Wachowski", "Writer, Screenplay"))),
                CreditsDepartment("Costume & Make-Up", listOf(person(12, "Kym Barrett", "Costume Designer"))),
                CreditsDepartment("Second Unit", listOf(person(13, "Someone Else", "Driver"))),
            ),
    )

private fun longCredits() =
    TitleCredits(
        cast = listOf(person(1, "Elijah Wood-Montgomery-Fitzgerald", "Frodo Baggins of the Shire, bearer of the one ring, son of Drogo")),
        departments =
            listOf(
                CreditsDepartment("Costume & Make-Up", listOf(person(2, "Ngila Dickson-Featherstonehaugh", "Costume Designer"))),
                CreditsDepartment("Visual Effects", listOf(person(3, "Jim Rygiel", "Visual Effects Supervisor, Digital Compositor"))),
            ),
    )

/** The whole cast and crew: the layout once across the device matrix, then the states and the worst case. */
class TitleCreditsScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun credits() = TitleCreditsScreen(TitleCreditsUiState.Ready(sampleCredits()), {}, {})

    /** The Crew tab: the departments under their headings. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun crew() = TitleCreditsScreen(TitleCreditsUiState.Ready(sampleCredits()), {}, {}, initialTab = 1)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = TitleCreditsScreen(TitleCreditsUiState.Loading, {}, {})

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun error() = TitleCreditsScreen(TitleCreditsUiState.Error(SeerrError.Unreachable), {}, {})

    /** The longest names and roles at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = TitleCreditsScreen(TitleCreditsUiState.Ready(longCredits()), {}, {})

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = TitleCreditsScreen(TitleCreditsUiState.Ready(longCredits()), {}, {})
}
