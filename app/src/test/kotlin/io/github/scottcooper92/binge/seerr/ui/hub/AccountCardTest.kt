package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The hub's account card opens the user's page as one control: one focus stop, not the card and a chevron (#1241). */
@RunWith(RobolectricTestRunner::class)
class AccountCardTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    @Test
    fun `the card is one control that opens the user's page`() {
        var opened = 0
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                AccountCard(account = HubAccount(id = 7, name = "Ana", isAdmin = false, avatarUrl = null), quota = null) { opened++ }
            }
        }

        assertEquals(1, rule.onAllNodes(clickable).fetchSemanticsNodes().size)
        rule.onNodeWithText("Ana").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `without an onClick the card is not a control`() {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                AccountCard(account = HubAccount(id = 7, name = "Ana", isAdmin = false, avatarUrl = null), quota = null)
            }
        }

        assertEquals(0, rule.onAllNodes(clickable).fetchSemanticsNodes().size)
    }
}
