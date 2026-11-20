package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A request row's two targets: the poster opens the title's page, and the rest of the row opens the request. */
@RunWith(RobolectricTestRunner::class)
class RequestRowPosterTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val taps = mutableListOf<String>()

    private val item =
        requestItem(id = 7, tmdbId = 603, title = "The Matrix", year = null, requestedBy = null, requestedById = null, status = null)

    private fun show(onOpenTitle: (() -> Unit)?) {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestRow(item = item, onClick = { taps += "request" }, onOpenTitle = onOpenTitle)
            }
        }
        rule.waitForIdle()
    }

    private fun poster() = hasContentDescription("Open The Matrix")

    @Test
    fun `a tap on the poster opens the title and not the request`() {
        show { taps += "title" }

        rule.onNode(poster()).assertIsDisplayed().performClick()

        assertEquals(listOf("title"), taps)
    }

    @Test
    fun `a tap on the rest of the row opens the request and not the title`() {
        show { taps += "title" }

        rule.onNodeWithText("The Matrix").performClick()

        assertEquals(listOf("request"), taps)
    }

    @Test
    fun `without a title page to open the poster is no target and is not announced as one`() {
        show(onOpenTitle = null)

        rule.onNode(poster()).assertDoesNotExist()
        rule.onNodeWithText("The Matrix").performClick()

        assertEquals(listOf("request"), taps)
    }
}
