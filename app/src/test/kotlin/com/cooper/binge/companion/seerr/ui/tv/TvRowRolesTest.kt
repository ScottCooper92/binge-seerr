package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.tv.component.TvChoiceRow
import com.binge.designsystem.tv.component.TvChoiceUi
import com.binge.designsystem.tv.component.TvOptionRow
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A TV option and a sign-in mode read as radio buttons and an action sheet row as a button, not as bare clickables (#1041). */
@RunWith(RobolectricTestRunner::class)
class TvRowRolesTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun `an option row is a radio button and an action row is a button`() {
        composeTestRule.setContent {
            BingeTvTheme {
                Column {
                    TvOptionRow(label = "Radarr", selected = true, onClick = {})
                    TvSideSheetRow(label = "Approve", onClick = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Radarr").assert(role(Role.RadioButton))
        composeTestRule.onNodeWithText("Approve").assert(role(Role.Button))
    }

    @Test
    fun `a sign-in mode is a radio button`() {
        composeTestRule.setContent {
            BingeTvTheme {
                TvChoiceRow(
                    choices = listOf(TvChoiceUi("local", "Local account"), TvChoiceUi("key", "API key")),
                    selectedKey = "local",
                    onSelect = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Local account").assert(role(Role.RadioButton))
        composeTestRule.onNodeWithText("API key").assert(role(Role.RadioButton))
    }
}
