package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.component.DetailActions
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Block is a round button in the action row, and a dialog asks before anything is sent. */
@RunWith(RobolectricTestRunner::class)
class TitleBlockTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val block get() = resources.getString(R.string.title_page_action_block)

    private fun actions(
        availability: TitleAvailability,
        mayBlock: Boolean,
        onBlock: () -> Unit,
    ) {
        val model =
            titleActionsModel(VersionState(availability, mayRequest = true), null, emptyList(), mayReport = false, mayBlock = mayBlock)
        rule.setContent {
            SeerrTheme {
                DetailActions(
                    primary = null,
                    roundActions = titleRoundActions(model, onTrailer = null, watchlist = null, onReport = null, onBlock = onBlock),
                )
            }
        }
    }

    @Test
    fun `a viewer who manages the blocklist taps Block on an unrequested title`() {
        var asked = 0
        actions(TitleAvailability.None, mayBlock = true) { asked++ }

        rule.onNodeWithText(block, useUnmergedTree = true).performClick()

        assertEquals(1, asked)
    }

    @Test
    fun `a viewer who may not manage the blocklist sees no Block`() {
        actions(TitleAvailability.None, mayBlock = false) {}

        rule.onNodeWithText(block, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a title the server already holds shows no Block`() {
        actions(TitleAvailability.Pending, mayBlock = true) {}

        rule.onNodeWithText(block, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the confirm names the title and nothing is sent until it is accepted`() {
        var confirmed = 0
        var cancelled = 0
        rule.setContent { SeerrTheme { TitleBlockConfirm("The Matrix", TitleBlock({}, { confirmed++ }, { cancelled++ })) } }

        rule.onNodeWithText(resources.getString(R.string.title_block_confirm_title, "The Matrix")).assertExists()
        assertEquals(0, confirmed)
        rule.onNodeWithText(block).performClick()

        assertEquals(1, confirmed)
        assertEquals(0, cancelled)
    }
}
