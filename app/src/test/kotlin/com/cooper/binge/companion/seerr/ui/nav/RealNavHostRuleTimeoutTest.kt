package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.ComposeTimeoutException
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a wait that times out says, since a failure that only shows in a full run has to explain itself (#1794). */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w900dp-h800dp")
class RealNavHostRuleTimeoutTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @Test
    fun `a wait that times out says what was waited for, the back stack and what the screen showed`() {
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.demo_indicator))

        val timeout =
            assertThrows(ComposeTimeoutException::class.java) { host.awaitShowing("no such text on any screen", timeoutMillis = 300) }

        val message = timeout.message.orEmpty()
        assertTrue(message, "\"no such text on any screen\" on screen" in message)
        assertTrue(message, "Back stack: [" in message)
        assertTrue("the screen's own text is in it: $message", host.string(R.string.demo_indicator) in message)
    }

    @Test
    fun `a tree longer than the cap is cut, and says how much was left out`() {
        val tree = (1..1000).joinToString("\n") { "node $it" }

        val capped = tree.cappedAt(300).lines()

        assertEquals(301, capped.size)
        assertEquals("node 300", capped[299])
        assertEquals("... 700 more lines", capped.last())
    }

    @Test
    fun `a tree within the cap is left as it is`() {
        assertEquals("a\nb\nc", "a\nb\nc".cappedAt(300))
    }
}
