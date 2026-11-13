package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
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
}
