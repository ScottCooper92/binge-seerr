package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.cooper.binge.companion.seerr.ui.requests.SheetTitleRow
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TitleSheetRowsTest {
    @get:Rule val rule: ComposeContentTestRule = createSeerrComposeRule()

    private fun rows(
        state: TitleState,
        fourKOpen: Boolean = false,
        remainingOpen: Boolean = false,
    ): List<String> {
        var labels = emptyList<String>()
        rule.setContent {
            labels = rememberTitleSheetRows(TitleSheetFacts(state, fourKOpen, remainingOpen), {}).labels()
        }
        rule.waitForIdle()
        return labels
    }

    private fun List<SheetTitleRow>.labels() = map { it.label }

    @Test
    fun `an available title offers Request in 4K and no Watch, which is the page's primary`() {
        assertEquals(listOf("Request in 4K"), rows(TitleState.Available, fourKOpen = true))
    }

    @Test
    fun `Request in 4K needs the permission`() {
        assertEquals(emptyList<String>(), rows(TitleState.Available))
    }

    @Test
    fun `a partly available title with seasons left offers the remaining seasons before Request in 4K`() {
        assertEquals(
            listOf("Request the remaining seasons", "Request in 4K"),
            rows(TitleState.PartiallyAvailable, fourKOpen = true, remainingOpen = true),
        )
    }

    @Test
    fun `the remaining seasons are offered only for a title in the library`() {
        assertEquals(emptyList<String>(), rows(TitleState.Processing, remainingOpen = true))
    }
}
