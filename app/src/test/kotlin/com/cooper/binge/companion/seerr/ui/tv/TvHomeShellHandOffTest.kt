package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsActions
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsSheet
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val TOP_ROW_CLEARANCE = 120.dp
private const val HEAT = "Heat"
private const val BEAR = "The Bear"

/**
 * Where focus lands as the top row and the sheets hand it around, in the real shell: Down from the top row returns to the card
 * the user came from, and Edit connection leaves focus in the form it opens rather than on the gear the sheet's close would
 * otherwise return it to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvHomeShellHandOffTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var editing by mutableStateOf(false)

    @Test
    fun downFromTheTopRowReturnsToTheCardTheUserCameFrom() {
        setHomeWithRequests()
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        composeTestRule.press(Key.DirectionUp)
        focusedTopRowButton().assertExists()
        composeTestRule.press(Key.DirectionDown)

        card(HEAT).assertIsFocused()
    }

    @Test
    fun editConnectionLeavesFocusInTheFormItOpens() {
        setHomeWithSettingsSheet()
        composeTestRule.press(Key.DirectionUp)
        repeat(TvDestination.entries.size) { composeTestRule.press(Key.DirectionRight) }
        composeTestRule.press(Key.DirectionCenter)
        composeTestRule.onNodeWithText(string(R.string.hub_disconnect)).assertExists()
        composeTestRule.onNode(hasText(string(R.string.settings_group_connection)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        composeTestRule.press(Key.DirectionCenter)
        composeTestRule.onNode(hasText(string(R.string.settings_edit_connection)) and isFocusable()).assertIsFocused()

        composeTestRule.press(Key.DirectionCenter)
        composeTestRule.mainClock.advanceTimeBy(FOCUS_SETTLE_MILLIS)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(OVERLAY_TAG).assertIsFocused()
    }

    private fun setHomeWithRequests() {
        val requests = listOf(request(1, HEAT, SeerrRequestStatusCode.Pending), request(2, BEAR, SeerrRequestStatusCode.Approved))
        composeTestRule.setContent {
            BingeTvTheme {
                var page by rememberSaveable { mutableStateOf(TvDestination.Hub) }
                TvHomeShell(page = page, onOpenPage = { page = it }, onClosePage = { page = TvDestination.Hub }) { shown, _ ->
                    if (shown == TvDestination.Hub) {
                        TvRequestsRowsBoard(
                            state =
                                RequestsUiState.Ready(
                                    filter = RequestFilter.All,
                                    sort = RequestSort.Added,
                                    counts = RequestCounts(total = 2, pending = 1, approved = 1, processing = 0, available = 0),
                                    scope =
                                        ModerationScope(
                                            SeerrPermissions(canManageRequests = true),
                                            currentUserId = 7,
                                            hasBlocklist = false,
                                        ),
                                    listVersion = 0,
                                ),
                            rowsFor = { filter ->
                                val items =
                                    when (filter) {
                                        RequestFilter.Pending -> requests.take(1)
                                        RequestFilter.Approved -> requests.drop(1)
                                        else -> emptyList()
                                    }
                                TvPagedRows(count = items.size, at = { items.getOrNull(it) })
                            },
                            actions = TvRequestsActions(onOpenDetail = {}, onSeeAll = {}, onRetryLoad = {}, onRetryScope = {}),
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun setHomeWithSettingsSheet() {
        composeTestRule.setContent {
            BingeTvTheme {
                var page by rememberSaveable { mutableStateOf(TvDestination.Hub) }
                TvHomeShell(
                    page = page,
                    onOpenPage = { page = it },
                    onClosePage = { page = TvDestination.Hub },
                    overlay = if (editing) ({ StandInPage(onClose = { editing = false }) }) else null,
                ) { shown, close ->
                    when (shown) {
                        TvDestination.Hub -> ClaimingBox()
                        TvDestination.Settings ->
                            TvSettingsSheet(
                                state =
                                    SettingsUiState.Ready(
                                        connection = ConnectionSummary("http://seerr.lan:5055", SignInKind.Session, "Scott"),
                                        server =
                                            ServerSummary(
                                                "Seerr",
                                                SeerrVariant.Jellyseerr,
                                                "2.7.2",
                                                updateAvailable = false,
                                                commitsBehind = 0,
                                            ),
                                        config = null,
                                    ),
                                actions =
                                    TvSettingsActions(
                                        onEditConnection = {
                                            editing = true
                                            close()
                                        },
                                    ),
                                onDismissRequest = close,
                            )
                        else -> Unit
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /** A Home that, like a board hosted as an overlay, claims first focus itself. */
    @androidx.compose.runtime.Composable
    private fun ClaimingBox() {
        val claim = remember { FocusRequester() }
        LaunchedEffect(Unit) { claim.requestFocus() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = TOP_ROW_CLEARANCE)
                    .focusRequester(claim)
                    .focusable(),
        )
    }

    private fun card(title: String) = composeTestRule.onNode(hasContentDescription(title) and isFocusable())

    /** Up from Home lands on whichever top-row button is nearest, so any of them being focused is the row. */
    private fun focusedTopRowButton() =
        composeTestRule.onNode(
            (
                hasContentDescription(string(R.string.tv_rail_account)) or
                    hasContentDescription(string(R.string.hub_section_issues)) or
                    hasContentDescription(string(R.string.hub_section_settings))
            ) and isFocused(),
        )

    private companion object {
        const val FOCUS_SETTLE_MILLIS = 1_000L
    }
}
