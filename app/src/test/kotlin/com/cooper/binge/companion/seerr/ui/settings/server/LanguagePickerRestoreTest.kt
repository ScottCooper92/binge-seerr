package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A tick applies at once and keeps the sheet open, and a sheet restored open after a rotation is still open and still has no Done. */
@RunWith(RobolectricTestRunner::class)
class LanguagePickerRestoreTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `a tick applies at once, and the sheet stays open through a rotation`() {
        val restoration = StateRestorationTester(rule)
        var saved: String? = null
        restoration.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                ItemGroup(
                    title = null,
                    rows =
                        listOf(
                            languageSettingItem(
                                icon = Icons.Filled.Language,
                                title = "Discover languages",
                                value = "eu",
                                // Basque and Galician, as no device suggests either.
                                choices = ListChoices.Ready(listOf(ListEntry("eu", "Basque"), ListEntry("gl", "Galician"))),
                                enabled = true,
                                onOpen = {},
                                onSelect = { saved = it },
                            ),
                        ),
                )
            }
        }

        rule.onNodeWithText("Discover languages").performClick()
        rule.onNodeWithText("Galician").performClick()

        assertEquals("eu|gl", saved)
        rule.onNodeWithText("Galician").assertIsDisplayed()
        rule.onNodeWithText("Done").assertDoesNotExist()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("Galician").assertIsDisplayed()
        rule.onNodeWithText("Done").assertDoesNotExist()
    }
}
