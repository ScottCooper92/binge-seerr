package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.hub.AccountBar
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Search tab's bar carries the account, which opens the user's own page (#2366). */
@RunWith(RobolectricTestRunner::class)
class TitleSearchAccountTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var opened: Int? = null

    private val actions = TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {})

    private fun showSearch(accountBar: AccountBar?) {
        rule.setContent {
            SeerrTheme {
                TitleSearchScreen(
                    state = TitleSearchUiState.Idle(text = "", recents = emptyList(), trending = Trending.Unavailable),
                    actions = actions,
                    accountBar = accountBar,
                    onOpenAccount = { opened = it },
                )
            }
        }
    }

    @Test
    fun `the account shows in Search's bar and opens the user's page`() {
        showSearch(AccountBar(HubAccount(id = 7, name = "Ana", isAdmin = false, avatarUrl = null), quota = null))

        rule.onNodeWithContentDescription("Ana").performClick()

        assertEquals(7, opened)
    }

    @Test
    fun `without an account Search's bar has none`() {
        showSearch(null)

        rule.onNodeWithContentDescription("Ana").assertDoesNotExist()
    }
}
