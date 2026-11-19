package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.discover.person.PersonGender
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The person page's loading skeleton starts its profile where the loaded profile starts, on a phone and on a wide window, where the
 * page's reading column is centred and the skeleton once kept to the screen's padding and so shifted when the page arrived (#2294).
 * Only the position is held: the skeleton's bars stand in for text of any length.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonSkeletonGeometryTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val person =
        PersonDetails(
            personId = 5,
            name = "Sam Example",
            knownFor = "Acting",
            biography = "A made-up actor.",
            birthday = "1980-02-14",
            deathday = null,
            placeOfBirth = "Demo City",
            alsoKnownAs = emptyList(),
            gender = PersonGender.Female,
            popularity = 12.4,
            imdbId = null,
            homepage = null,
            photoUrl = null,
            webUrl = "https://seerr.example/person/5",
        )

    private fun check() {
        rule.assertSkeletonReservesGeometry(
            listOf(LayoutAnchors.section(LayoutAnchors.Detail.PROFILE)),
            checkStartEdge = true,
            checkSize = false,
        ) { resolved ->
            SeerrTheme {
                PersonPageScreen(
                    if (resolved) {
                        PersonPageUiState.Ready(
                            person,
                            PersonCreditsState.Loading,
                            refreshing = false,
                        )
                    } else {
                        PersonPageUiState.Loading
                    },
                    PersonPageActions({}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }),
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `the skeleton's profile starts where the loaded one does on a phone`() = check()

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun `the skeleton's profile starts where the loaded one does on an 800dp window`() = check()

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `the skeleton's profile starts where the loaded one does on a 1280dp window`() = check()
}
