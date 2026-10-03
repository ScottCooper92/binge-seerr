package io.github.scottcooper92.binge.seerr.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * Content a test takes down itself, before it ends.
 *
 * A text field still focused when the rule disposes the composition leaves work pending on Compose's
 * shared UI dispatcher that Robolectric then discards, and that dispatcher never runs anything again.
 * The next test to collect `LazyPagingItems` (which captured it) stays on its loading state for good,
 * so a focus test sorted ahead of a pager test fails that test, not its own. Removing the content
 * while the looper is still live lets the dispatcher drain. Call [remove] in an `@After`.
 */
class RemovableContent(
    private val rule: ComposeContentTestRule,
) {
    private var shown by mutableStateOf(true)

    fun setContent(content: @Composable () -> Unit) = rule.setContent { if (shown) content() }

    fun remove() {
        shown = false
        rule.waitForIdle()
    }
}
