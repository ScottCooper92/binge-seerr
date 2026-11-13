package com.cooper.binge.companion.seerr.ui.settings.licences

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LicencesScreenTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private val library = LicenceEntry("okhttp", "OkHttp", "5.0.0", listOf("Apache-2.0"), "https://square.github.io/okhttp/")

    private fun show(
        state: LicencesUiState,
        onOpenWebsite: (String) -> Unit = {},
    ) = composeTestRule.setContent {
        BingeExpressiveTheme(dynamicColor = false) { LicencesScreen(state = state, onBack = {}, onOpenWebsite = onOpenWebsite) }
    }

    @Test
    fun `a library shows its name, version and licence and opens its website`() {
        val opened = mutableListOf<String>()
        show(LicencesUiState.Ready(listOf(library)), opened::add)
        composeTestRule.onNodeWithText("OkHttp").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("5.0.0 · Apache-2.0").assertIsDisplayed()
        assertEquals(listOf("https://square.github.io/okhttp/"), opened)
    }

    @Test
    fun `a library with no website is not a link`() {
        val opened = mutableListOf<String>()
        show(LicencesUiState.Ready(listOf(library.copy(website = null))), opened::add)
        composeTestRule.onNodeWithText("OkHttp").performClick()
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `an empty list and an unreadable one each say so`() {
        show(LicencesUiState.Ready(emptyList()))
        composeTestRule.onNodeWithText(string(R.string.licences_empty)).assertIsDisplayed()
    }

    @Test
    fun `an unreadable list says so`() {
        show(LicencesUiState.Unreadable)
        composeTestRule.onNodeWithText(string(R.string.licences_unreadable)).assertIsDisplayed()
    }
}
