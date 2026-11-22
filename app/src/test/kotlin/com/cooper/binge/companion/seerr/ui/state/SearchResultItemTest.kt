package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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

/** A search result is a tick row: announced as checked or not, and a tap toggles it. */
@RunWith(RobolectricTestRunner::class)
class SearchResultItemTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val toggled = mutableListOf<String>()

    @Test
    fun `an added result reads as ticked and an open one does not, and a tap toggles either`() {
        rule.setContent {
            BingeExpressiveTheme {
                ItemGroup(
                    title = null,
                    rows =
                        listOf(
                            searchResultItem("Pixar", added = true, onToggle = { toggled += "Pixar" }, icon = Icons.Filled.Business),
                            searchResultItem("Ghibli", added = false, onToggle = { toggled += "Ghibli" }, icon = Icons.Filled.Business),
                        ),
                )
            }
        }
        rule.onNodeWithText("Pixar").assertIsOn()
        rule.onNodeWithText("Ghibli").assertIsOff()
        rule.onNodeWithText("Ghibli").performClick()
        rule.onNodeWithText("Pixar").performClick()
        assertEquals(listOf("Ghibli", "Pixar"), toggled)
    }
}
