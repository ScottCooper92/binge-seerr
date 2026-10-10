package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.IssueReport
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.MediaInstance
import io.github.scottcooper92.binge.seerr.ui.requests.MediaRecord
import io.github.scottcooper92.binge.seerr.ui.requests.MediaStatusChoice
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetail
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailScreen
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardAndroidComposeRule
import io.github.scottcooper92.binge.seerr.util.requestDetail
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val MEDIA_ID = 9

/** The two sheets the TV request page gained from the phone's (#1036): marking the title's state, and reporting an issue. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestDetailSheetsTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private val marked = mutableListOf<Triple<Int, MediaStatusChoice, Boolean>>()
    private val reported = mutableListOf<Pair<IssueType, String>>()
    private var dismissedReports = 0

    @Test
    fun markAsOpensOnTheStatesAndAPickApplies() {
        setContent(detail(media = media(MediaInstance(false, SeerrMediaStatusCode.Processing, null, null, null))))
        actionButton(R.string.media_mark_as).assertIsFocused()

        pressOk()
        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertIsDisplayed()
        pressOk()

        assertEquals(listOf(Triple(MEDIA_ID, MediaStatusChoice.Available, false)), marked)
        actionButton(R.string.media_mark_as).assertIsFocused()
    }

    @Test
    fun aTitleHeldTwiceAsksWhichInstanceFirst() {
        setContent(
            detail(
                media =
                    media(
                        MediaInstance(false, SeerrMediaStatusCode.Available, null, null, null),
                        MediaInstance(true, SeerrMediaStatusCode.Processing, null, null, null),
                    ),
            ),
        )
        pressOk()
        composeTestRule.onNode(hasText(string(R.string.settings_service_4k)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        pressOk()

        assertEquals(listOf(Triple(MEDIA_ID, MediaStatusChoice.Available, true)), marked)
    }

    @Test
    fun aReportSendsTheKindAndTheMessage() {
        setContent(detail(canReportIssue = true))
        actionButton(R.string.issue_report_title).assertIsFocused()

        pressOk()
        composeTestRule.onNode(hasText(string(R.string.issue_type_audio)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("No sound after the intro")
        composeTestRule.onNode(hasText(string(R.string.issue_report_send)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()

        assertEquals(listOf(IssueType.Audio to "No sound after the intro"), reported)
    }

    @Test
    fun aSentReportSaysSoAndDoneClosesIt() {
        setContent(detail(canReportIssue = true), report = IssueReport.Sent)
        pressOk()

        composeTestRule.onNodeWithText(string(R.string.issue_report_sent)).assertIsDisplayed()
        composeTestRule.onNode(hasText(string(R.string.editor_done)) and isFocusable()).assertIsFocused()
        pressOk()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        assertEquals(1, dismissedReports)
        actionButton(R.string.issue_report_title).assertIsFocused()
    }

    @Test
    fun neitherIsOfferedWithoutItsGate() {
        setContent(detail(actions = RequestActions(canRemove = true)))

        actionButton(R.string.media_mark_as).assertDoesNotExist()
        actionButton(R.string.issue_report_title).assertDoesNotExist()
    }

    private fun media(vararg instances: MediaInstance) =
        MediaRecord(
            mediaId = MEDIA_ID,
            isTv = false,
            instances = instances.toList(),
            canSetStatus = true,
            canClearData = false,
            canDeleteFiles = false,
        )

    private fun detail(
        media: MediaRecord? = null,
        canReportIssue: Boolean = false,
        actions: RequestActions = RequestActions(),
    ) = requestDetail(actions = actions, mediaId = MEDIA_ID, canReportIssue = canReportIssue, media = media)

    private fun setContent(
        detail: RequestDetail,
        report: IssueReport = IssueReport.Idle,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestDetailScreen(
                    state = RequestDetailUiState.Ready(detail, report = report),
                    events = emptyFlow(),
                    actions =
                        TvRequestDetailActions(
                            onBack = {},
                            onRetry = {},
                            onOpenInBinge = null,
                            onApprove = {},
                            onRetryRequest = {},
                            onDecline = {},
                            onRemove = {},
                            onBlock = {},
                            onSetMediaStatus = { mediaId, status, is4k -> marked += Triple(mediaId, status, is4k) },
                            onReportIssue = { type, message -> reported += type to message },
                            onDismissReport = { dismissedReports++ },
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** A resting icon button names itself by description and a labelled one by text; either way the surface is the focusable node. */
    private fun actionButton(label: Int) =
        composeTestRule.onNode(
            isFocusable() and
                (hasContentDescription(string(label)) or hasText(string(label)) or hasAnyDescendant(hasText(string(label)))),
        )

    private fun pressOk() {
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}
