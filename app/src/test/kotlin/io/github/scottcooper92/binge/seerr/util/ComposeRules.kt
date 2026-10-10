package io.github.scottcooper92.binge.seerr.util

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.testing.TakeDownAndroidComposeRule
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.binge.designsystem.testing.createTakeDownComposeRule
import com.binge.designsystem.testing.createTakeDownKeyboardAndroidComposeRule
import com.binge.designsystem.testing.createTakeDownKeyboardComposeRule

/**
 * The module's compose rule: the design system's take-down rule, which removes the content before the rule disposes it.
 * Every Robolectric Compose test here uses this one, or [createSeerrAndroidComposeRule] when it needs the activity;
 * [ComposeRuleConventionTest] holds that. The rule and the reason for it (a focused text field leaves work on the
 * shared UI dispatcher, which starves the next test's `LazyPagingItems`, #663) live in the design system's test fixtures.
 */
fun createSeerrComposeRule(): ComposeContentTestRule = createTakeDownComposeRule()

/**
 * [createSeerrComposeRule] in keyboard input mode, for a test that drives focus with a D-pad or Tab. A TV never enters
 * touch mode, so this is the faithful fixture rather than a workaround.
 */
fun createSeerrKeyboardComposeRule(): ComposeContentTestRule = createTakeDownKeyboardComposeRule()

/** [createSeerrComposeRule] for a test that needs the hosting activity, such as one pressing Back through its dispatcher. */
inline fun <reified A : ComponentActivity> createSeerrAndroidComposeRule(): TakeDownAndroidComposeRule<A> =
    createTakeDownAndroidComposeRule<A>()

/** [createSeerrAndroidComposeRule] in keyboard input mode; see [createSeerrKeyboardComposeRule]. */
inline fun <reified A : ComponentActivity> createSeerrKeyboardAndroidComposeRule(): TakeDownAndroidComposeRule<A> =
    createTakeDownKeyboardAndroidComposeRule<A>()
