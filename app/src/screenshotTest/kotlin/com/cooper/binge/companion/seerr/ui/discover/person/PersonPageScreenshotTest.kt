package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.person.PersonCredits
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.state.RestingPull

private val NO_ACTIONS = PersonPageActions({}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> })

@Composable
private fun Frame(state: PersonPageUiState) =
    PersonPageScreen(
        state,
        NO_ACTIONS,
        pullState = RestingPull.takeIf { (state as? PersonPageUiState.Ready)?.refreshing == true },
    )

/** The person page: the layout once across the device matrix, then each state of it on the phone. */
class PersonPageScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun full() = Frame(readyPerson())

    /** The server knows only the name and the person has no credits: no empty section is drawn. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun sparse() = Frame(readyPerson(sparsePerson(), PersonCreditsState.Ready(PersonCredits(emptyList(), emptyList()))))

    /** A person who has died, with no links: the age is stated, and no links row is drawn. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun diedNoLinks() = Frame(readyPerson(lateWithoutLinks()))

    /** A pull in flight: the spinner sits in the bar, clear of the name. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun refreshing() = Frame(readyPerson(refreshing = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun creditsLoading() = Frame(readyPerson(credits = PersonCreditsState.Loading))

    /** The credits failed to read: the profile stays and the failure sits in place with its Retry. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun creditsFailed() = Frame(readyPerson(credits = PersonCreditsState.Error(SeerrError.Unreachable)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(PersonPageUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = Frame(PersonPageUiState.Error(SeerrError.Unreachable))

    /** A full person at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun fullLargeFonts() = Frame(readyPerson())

    /** A sparse person at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun sparseLargeFonts() = Frame(readyPerson(sparsePerson(), PersonCreditsState.Ready(PersonCredits(emptyList(), emptyList()))))

    /** The longest names at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = Frame(readyPerson(longPerson(), PersonCreditsState.Ready(fullCredits(longNames = true))))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = Frame(readyPerson(longPerson(), PersonCreditsState.Ready(fullCredits(longNames = true))))
}
