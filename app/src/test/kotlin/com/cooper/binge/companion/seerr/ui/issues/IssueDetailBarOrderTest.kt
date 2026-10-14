package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The top bar of an issue's page ends with its destructive action: Delete comes after the manage button (#1531). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class IssueDetailBarOrderTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `delete is the last action on the bar, after the manage button`() {
        rule.setContent {
            SeerrTheme {
                IssueDetailScreen(
                    state = IssueDetailUiState.Ready(detail = commonIssueDetail()),
                    events = emptyFlow(),
                    actions = noIssueDetailActions(),
                )
            }
        }

        val manage = rule.onNodeWithContentDescription(string(R.string.issue_manage_cd)).getBoundsInRoot()
        val delete = rule.onNodeWithContentDescription(string(R.string.issue_delete)).getBoundsInRoot()

        assertTrue("Delete (${delete.left}) sits right of manage (${manage.left})", delete.left >= manage.right)
    }
}
