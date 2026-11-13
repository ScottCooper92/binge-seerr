package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewPromptOnReturnToRootTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private var connected by mutableStateOf<Boolean?>(null)
    private var depth by mutableStateOf(0)
    private var asked = 0

    private fun host() = composeTestRule.setContent { ReturnToRootEffect(connected, depth) { asked++ } }

    @Test
    fun `starting up and connecting to the root is not a return`() {
        host()
        composeTestRule.waitForIdle()
        connected = true
        depth = 1
        composeTestRule.waitForIdle()
        assertEquals(0, asked)
    }

    @Test
    fun `signing in from setup is not a return`() {
        connected = false
        depth = 1
        host()
        composeTestRule.waitForIdle()
        connected = true
        composeTestRule.waitForIdle()
        assertEquals(0, asked)
    }

    @Test
    fun `going from the root to a detail and back asks once`() {
        connected = true
        depth = 1
        host()
        composeTestRule.waitForIdle()
        depth = 2
        composeTestRule.waitForIdle()
        assertEquals(0, asked)
        depth = 1
        composeTestRule.waitForIdle()
        assertEquals(1, asked)
    }

    @Test
    fun `losing the connection while the ask is in flight cancels it`() {
        var started = false
        var cancelled = false
        connected = true
        depth = 2
        composeTestRule.setContent {
            ReturnToRootEffect(connected, depth) {
                started = true
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        composeTestRule.waitForIdle()
        depth = 1
        composeTestRule.waitForIdle()
        assertTrue(started)
        assertFalse(cancelled)
        connected = false
        composeTestRule.waitForIdle()
        assertTrue(cancelled)
    }

    @Test
    fun `signing in again after losing the connection while deep is not a return`() {
        connected = true
        depth = 2
        host()
        composeTestRule.waitForIdle()
        connected = false
        depth = 1
        composeTestRule.waitForIdle()
        connected = true
        composeTestRule.waitForIdle()
        assertEquals(0, asked)
    }
}
