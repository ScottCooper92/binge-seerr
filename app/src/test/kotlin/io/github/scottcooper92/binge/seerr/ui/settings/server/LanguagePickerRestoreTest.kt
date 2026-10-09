package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** #949: a language sheet restored open, after a rotation, keeps the ticks made before it. */
@RunWith(RobolectricTestRunner::class)
class LanguagePickerRestoreTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `ticks made before a rotation are still there to save after it`() {
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
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Done").performClick()

        assertEquals("eu|gl", saved)
    }
}
