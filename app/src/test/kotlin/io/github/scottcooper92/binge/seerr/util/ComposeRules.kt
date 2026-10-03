package io.github.scottcooper92.binge.seerr.util

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The module's compose rule: [createComposeRule], with the content taken down before the rule
 * disposes it. Every Robolectric Compose test here uses this one, or [createSeerrAndroidComposeRule] when it
 * needs the activity; [ComposeRuleConventionTest] holds that.
 *
 * A text field still focused when the rule disposes the composition leaves work pending on Compose's
 * shared UI dispatcher. Robolectric discards that work, and the dispatcher never runs anything again.
 * `LazyPagingItems` captures that dispatcher, so the next test to collect paged items stays on its
 * loading state for good: a focus test sorted ahead of a pager test fails the pager test, not itself
 * (#663). Removing the content while the looper is still live lets the dispatcher drain, and doing it
 * here means no test has to remember to.
 */
fun createSeerrComposeRule(): ComposeContentTestRule = TakeDownComposeRule(createComposeRule())

/**
 * [createSeerrComposeRule] for a test that needs the hosting activity, such as one pressing Back through
 * its dispatcher: the same take-down around `createAndroidComposeRule`, with [SeerrAndroidComposeRule.activity]
 * passed through (#670).
 */
inline fun <reified A : ComponentActivity> createSeerrAndroidComposeRule(): SeerrAndroidComposeRule<A> =
    SeerrAndroidComposeRule(createAndroidComposeRule<A>())

/** An activity-hosted compose rule whose content is taken down before it disposes; see [createSeerrComposeRule]. */
class SeerrAndroidComposeRule<A : ComponentActivity>(
    private val rule: AndroidComposeTestRule<*, A>,
) : ComposeContentTestRule by TakeDownComposeRule(rule) {
    val activity: A get() = rule.activity
}

internal class TakeDownComposeRule(
    private val rule: ComposeContentTestRule,
) : ComposeContentTestRule by rule {
    private var shown by mutableStateOf(true)
    private var composed = false

    override fun setContent(composable: @Composable () -> Unit) {
        composed = true
        rule.setContent { if (shown) composable() }
    }

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        rule.apply(
            object : Statement() {
                override fun evaluate() {
                    // The test's own failure wins over one in the take-down.
                    val outcome = runCatching { base.evaluate() }
                    val takenDown = runCatching { takeDown() }
                    outcome.getOrThrow()
                    takenDown.getOrThrow()
                }
            },
            description,
        )

    private fun takeDown() {
        if (!composed) return
        shown = false
        rule.waitForIdle()
    }
}
