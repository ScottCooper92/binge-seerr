package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.binge.designsystem.testing.assertNoTextClipped
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailScreen
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.commonIssueDetail
import com.cooper.binge.companion.seerr.ui.issues.noIssueDetailActions
import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailPage
import com.cooper.binge.companion.seerr.ui.users.UserDetail
import com.cooper.binge.companion.seerr.ui.users.UserDetailActions
import com.cooper.binge.companion.seerr.ui.users.UserDetailScreen
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserWatch
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val OVERVIEW =
    "A long overview that runs on for several sentences, so that the page has a paragraph to wrap at a large font. " +
        "It keeps going past what any one screen shows at once, which is the case a clip is most likely to hide in."

/**
 * The app's detail pages with every optional line filled and long text, at font scales 1.3 and 2.0, each through the
 * design system's `assertNoTextClipped`. A page scrolls, so the tall viewport keeps the whole of it in view: text that
 * is only scrolled out is skipped by the assertion, and this is where a row inside a page is held to the same bar as one
 * in a list. Native graphics, because legacy graphics measures text at a pixel a character and nothing wraps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h8000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(
        fontScale: Float,
        page: @Composable () -> Unit,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                SeerrTheme { Box(Modifier.width(PHONE_WIDTH)) { page() } }
            }
        }
        rule.waitForIdle()
        val checked = rule.assertNoTextClipped()
        assertTrue("only $checked text nodes were checked", checked >= MIN_CHECKED)
    }

    @Composable
    private fun RequestPage() {
        RequestDetailPage(
            detail =
                requestDetail(
                    item = worstCaseRequest(),
                    actions = RequestActions(canApprove = true, canDecline = true, canRemove = true, canBlock = true),
                    canEdit = true,
                    overview = OVERVIEW,
                    canReportIssue = true,
                    canManageUsers = true,
                ),
            onBack = {},
            onOpen = {},
            onReport = {},
            onOpenRequest = {},
            onOpenUser = {},
            now = NOW_MILLIS,
        )
    }

    @Composable
    private fun IssuePage() {
        val detail = commonIssueDetail()
        IssueDetailScreen(
            state =
                IssueDetailUiState.Ready(
                    detail =
                        detail.copy(
                            item = worstCaseIssue(),
                            report = detail.report?.copy(author = LONG_NAME, message = OVERVIEW),
                            comments = detail.comments.map { it.copy(author = LONG_NAME, message = OVERVIEW) },
                        ),
                ),
            events = emptyFlow(),
            actions = noIssueDetailActions(),
        )
    }

    @Composable
    private fun UserPage() {
        UserDetailScreen(
            state =
                UserDetailUiState.Ready(
                    detail =
                        UserDetail(
                            item = worstCaseUser(),
                            permissions = setOf(ManageablePermission.Admin),
                            quota = null,
                            watch = UserWatch(playCount = 12_840, recentlyWatched = emptyList()),
                            watchlist = emptyList(),
                            isSelf = false,
                            canEditSettings = true,
                            canDelete = true,
                            serverUrl = "https://seerr.example/",
                            webUrl = "https://seerr.example/users/7",
                        ),
                ),
            requests = flowOf(PagingData.from(emptyList())),
            events = emptyFlow(),
            actions = UserDetailActions(onBack = {}, onRetry = {}, onOpenRequest = {}, onOpenSettings = {}, onDeleteUser = {}),
        )
    }

    @Test fun `a request page at 1_3`() = check(FONT_13) { RequestPage() }

    @Test fun `a request page at 2_0`() = check(FONT_20) { RequestPage() }

    @Test fun `an issue page at 1_3`() = check(FONT_13) { IssuePage() }

    @Test fun `an issue page at 2_0`() = check(FONT_20) { IssuePage() }

    @Test fun `a user page at 1_3`() = check(FONT_13) { UserPage() }

    @Test fun `a user page at 2_0`() = check(FONT_20) { UserPage() }

    private companion object {
        val PHONE_WIDTH = 412.dp
        const val NOW_MILLIS = 1_770_000_000_000L
        const val MIN_CHECKED = 3
        const val FONT_13 = 1.3f
        const val FONT_20 = 2.0f
    }
}
