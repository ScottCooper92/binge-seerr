package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestUser
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val ADA = RequestUser(9, "Ada")
private val GRACE = RequestUser(12, "Grace")

private fun sheet(
    users: UsersState,
    requestFor: RequestUser? = null,
) = RequestSheetUiState.Ready(
    target = RequestSheetTarget(kind = TitleKind.Movie, tmdbId = 603, title = "The Matrix", meta = "1999"),
    access = RequestAccess(may4k = false, mayChooseDestination = false, mayRequestForOthers = users != UsersState.None, viewerId = 9),
    quota = QuotaState.Known(null),
    users = users,
    requestFor = requestFor,
)

/** The Request for row: only for a user the server lets request for someone else, reading "Me" until another is picked. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class RequestForRowTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val picked = mutableListOf<Int>()

    private fun show(state: RequestSheetUiState.Ready) {
        rule.setContent {
            SeerrTheme {
                RequestSheetContent(
                    state,
                    RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onRequestFor = { picked += it }),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a user who may not request for others has no such row`() {
        show(sheet(UsersState.None))

        rule.onNodeWithText("Request for").assertDoesNotExist()
    }

    @Test
    fun `the row reads Me until another user is picked`() {
        show(sheet(UsersState.Ready(listOf(ADA, GRACE))))

        rule.onNodeWithText("Request for").assertExists()
        rule.onNodeWithText("Me").assertExists()
    }

    @Test
    fun `the row names the user the request is for`() {
        show(sheet(UsersState.Ready(listOf(ADA, GRACE)), requestFor = GRACE))

        rule.onNodeWithText("Grace").assertExists()
    }

    @Test
    fun `a tap opens the users and a pick makes the request for them`() {
        show(sheet(UsersState.Ready(listOf(ADA, GRACE))))

        rule.onNodeWithText("Request for").performClick()
        rule.onNodeWithText("Grace").performClick()

        assertEquals(listOf(12), picked)
    }

    @Test
    fun `a failed read says so`() {
        show(sheet(UsersState.Failed))

        rule.onNodeWithText("Could not load the users").assertExists()
    }
}
