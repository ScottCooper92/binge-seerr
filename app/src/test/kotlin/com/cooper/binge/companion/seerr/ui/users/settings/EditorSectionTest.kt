package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The section's contract as a screen reader and a remote see it: one button that says whether it is open,
 * an open body only while it is, a choice that survives the activity being recreated, and an error that
 * cannot be collapsed away. Appearance is the screenshot suite's.
 */
@RunWith(RobolectricTestRunner::class)
class EditorSectionTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun setSection(
        defaultExpanded: Boolean,
        issues: List<EditorIssue> = emptyList(),
    ) = rule.setContent {
        BingeExpressiveTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalEditorIssues provides issues) {
                EditorSection("s", "Connection", defaultExpanded = defaultExpanded) { Text("Body") }
            }
        }
    }

    @Test
    fun `the header is a button that reports it is collapsed and shows no body`() {
        setSection(defaultExpanded = false)

        rule.onNodeWithText("Connection").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithText("Connection").assert(state("Collapsed"))
        rule.onNodeWithText("Body").assertDoesNotExist()
    }

    @Test
    fun `activating the header opens the body and says so`() {
        setSection(defaultExpanded = false)

        rule.onNodeWithText("Connection").performClick()

        rule.onNodeWithText("Body").assertIsDisplayed()
        rule.onNodeWithText("Connection").assert(state("Expanded"))
    }

    @Test
    fun `an open section collapses again`() {
        setSection(defaultExpanded = true)
        rule.onNodeWithText("Body").assertIsDisplayed()

        rule.onNodeWithText("Connection").performClick()

        rule.onNodeWithText("Body").assertDoesNotExist()
    }

    /**
     * `StateRestorationTester` takes only a `ComposeUiTest`, so this one test leaves the rule: it is the one
     * place the repo needs to simulate recreation, and the rule has no equivalent.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the choice survives the activity being recreated`() =
        runComposeUiTest {
            val restoration = StateRestorationTester(this)
            restoration.setContent {
                BingeExpressiveTheme(dynamicColor = false) {
                    EditorSection("s", "Connection", defaultExpanded = false) { Text("Body") }
                }
            }
            onNodeWithText("Connection").performClick()

            restoration.emulateSaveAndRestore()

            onNodeWithText("Body").assertIsDisplayed()
        }

    @Test
    fun `a section with an issue opens itself, counts it, and cannot be collapsed`() {
        setSection(defaultExpanded = false, issues = listOf(invalid("s", "f", R.string.editor_error_port)))

        rule.onNodeWithText("Body").assertIsDisplayed()
        rule.onNodeWithText("1 field needs attention").assertIsDisplayed()
        rule.onNodeWithText("Connection").assertIsNotEnabled()
    }

    @Test
    fun `an issue in another section leaves this one alone`() {
        setSection(defaultExpanded = false, issues = listOf(invalid("other", "f", R.string.editor_error_port)))

        rule.onNodeWithText("Body").assertDoesNotExist()
        rule.onNodeWithText("Connection").assertIsEnabled()
    }

    @Test
    fun `the count is plural`() {
        setSection(
            defaultExpanded = false,
            issues = listOf(invalid("s", "a", R.string.editor_error_port), missing("s", "b")),
        )

        rule.onNodeWithText("2 fields need attention").assertIsDisplayed()
    }

    @Test
    fun `switching to another editor starts each section from its own default`() {
        var form by mutableStateOf(EditorFormState("user-1"))
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalEditorForm provides form) {
                    EditorSection("s", "Connection", defaultExpanded = false) { Text("Body") }
                }
            }
        }
        rule.onNodeWithText("Connection").performClick()
        rule.onNodeWithText("Body").assertIsDisplayed()

        form = EditorFormState("user-2")
        rule.waitForIdle()

        rule.onNodeWithText("Body").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `each section of one editor keeps its own choice through recreation`() =
        runComposeUiTest {
            val restoration = StateRestorationTester(this)
            restoration.setContent {
                BingeExpressiveTheme(dynamicColor = false) {
                    CompositionLocalProvider(LocalEditorForm provides remember { EditorFormState("user-1") }) {
                        Column {
                            EditorSection("a", "Connection", defaultExpanded = false) { Text("Connection body") }
                            EditorSection("b", "Advanced", defaultExpanded = true) { Text("Advanced body") }
                        }
                    }
                }
            }
            onNodeWithText("Connection").performClick()
            onNodeWithText("Advanced").performClick()

            restoration.emulateSaveAndRestore()

            onNodeWithText("Connection body").assertIsDisplayed()
            onNodeWithText("Advanced body").assertDoesNotExist()
        }

    @Test
    fun `a section an issue held open goes back to the user's choice once the issue clears`() {
        var issues by mutableStateOf(emptyList<EditorIssue>())
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalEditorIssues provides issues) {
                    EditorSection("s", "Connection", defaultExpanded = true) { Text("Body") }
                }
            }
        }
        rule.onNodeWithText("Connection").performClick()
        issues = listOf(invalid("s", "f", R.string.editor_error_port))
        rule.waitForIdle()
        rule.onNodeWithText("Body").assertIsDisplayed()

        issues = emptyList()
        rule.waitForIdle()

        rule.onNodeWithText("Body").assertDoesNotExist()
    }
}
