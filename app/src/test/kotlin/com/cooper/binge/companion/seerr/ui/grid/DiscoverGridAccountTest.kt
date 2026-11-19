package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.hub.AccountBar
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Discover grid's bar carries the account, which opens the user's own page (#2366). */
@RunWith(RobolectricTestRunner::class)
class DiscoverGridAccountTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var opened: Int? = null

    private fun show(accountBar: AccountBar?) {
        rule.setContent {
            SeerrTheme {
                DiscoverGridScreen(
                    kind = TitleKind.Movie,
                    title = null,
                    state = DiscoverGridUiState.Loading(GridControls(sort = null, chip = null)),
                    actions = DiscoverGridActions({}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, FilterLoads({}, {}, {})),
                    accountBar = accountBar,
                    onOpenAccount = { opened = it },
                )
            }
        }
    }

    @Test
    fun `the account shows in the bar and opens the user's page`() {
        show(AccountBar(HubAccount(id = 7, name = "Ana", isAdmin = false, avatarUrl = null), quota = null))

        rule.onNodeWithContentDescription("Ana").performClick()

        assertEquals(7, opened)
    }

    @Test
    fun `without an account the bar has none`() {
        show(null)

        rule.onNodeWithContentDescription("Ana").assertDoesNotExist()
    }
}
