package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.ui.Choice
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A server's tags are rows to tick, one line each, not chips stacked one to a line. */
@RunWith(RobolectricTestRunner::class)
class TagTicksTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val selected = mutableStateOf(setOf(1))
    private val tags = listOf(Choice(1, "kids"), Choice(2, "4k remux"))

    private fun show(enabled: Boolean = true) {
        rule.setContent {
            BingeExpressiveTheme {
                Column { TagRows(tags, selected.value, enabled) { id -> selected.value = selected.value.toggled(id) } }
            }
        }
    }

    @Test
    fun `a tag is ticked and unticked from its row`() {
        show()
        rule.onNodeWithText("4k remux").performClick()
        assertEquals(setOf(1, 2), selected.value)
        rule.onNodeWithText("kids").performClick()
        assertEquals(setOf(2), selected.value)
    }

    @Test
    fun `tags cannot be changed while the form is saving`() {
        show(enabled = false)
        rule.onNodeWithText("kids").assertIsNotEnabled()
    }
}
