package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.CompositionLocalProvider
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.paneBackOrNull
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SeerrNavHost] provides [LocalIsSinglePaneNav] and [LocalPaneDepth] together. The design system's
 * [paneBackOrNull] reads both, so providing only the first would hide the Back arrow on every stacked
 * screen beside the hub.
 */
@RunWith(RobolectricTestRunner::class)
class PaneBackLocalsTest {
    @get:Rule
    val compose = createSeerrComposeRule()

    private fun backWith(
        hubBeside: Boolean,
        paneDepth: Int,
    ): (() -> Unit)? {
        var result: (() -> Unit)? = null
        compose.setContent {
            CompositionLocalProvider(LocalIsSinglePaneNav provides !hubBeside, LocalPaneDepth provides paneDepth) {
                result = paneBackOrNull {}
            }
        }
        compose.waitForIdle()
        return result
    }

    @Test
    fun `a section directly beside the hub has no Back arrow`() {
        assertNull(backWith(hubBeside = true, paneDepth = 1))
    }

    @Test
    fun `a stacked screen beside the hub keeps its Back arrow`() {
        assertNotNull(backWith(hubBeside = true, paneDepth = 2))
    }

    @Test
    fun `a single pane always keeps its Back arrow`() {
        assertNotNull(backWith(hubBeside = false, paneDepth = 1))
    }
}
