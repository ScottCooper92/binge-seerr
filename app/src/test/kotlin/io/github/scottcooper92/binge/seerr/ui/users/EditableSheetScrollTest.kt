package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.scottcooper92.binge.seerr.ui.requests.IssueReport
import io.github.scottcooper92.binge.seerr.ui.requests.ReportIssueContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A sheet that takes typing has to scroll its own content (#293).
 *
 * A focused `OutlinedTextField` asks an ancestor to bring it into view. With no scrollable one
 * inside the sheet the request reaches the sheet's drag state, and the sheet itself moves — which is
 * the jump users reported. Asserting the scroll action is asserting that an ancestor exists to
 * answer the request; without it these two render with no scroll semantics at all.
 */
@RunWith(RobolectricTestRunner::class)
class EditableSheetScrollTest {
    @get:Rule
    val rule = createComposeRule()

    private fun assertScrolls() {
        // Vertical specifically: ReportIssueContent's type chips are a horizontalScroll row, so a
        // plain ScrollBy match passes there whether or not the body itself scrolls.
        val scrollsVertically = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        assertTrue(
            "the sheet's content has no vertically scrollable ancestor, so a focused field would move the sheet",
            rule.onAllNodes(scrollsVertically).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `the create-account sheet scrolls its own content`() {
        rule.setContent {
            CreateUserSheetContent(
                draft = CreateUserDraft(email = "ada@example.org", username = "ada"),
                saving = false,
                onEditDraft = {},
                onCreate = {},
            )
        }

        assertScrolls()
    }

    @Test
    fun `the report-issue sheet scrolls its own content`() {
        rule.setContent {
            ReportIssueContent(report = IssueReport.Idle, onSend = { _, _ -> })
        }

        assertScrolls()
    }
}
