package io.github.scottcooper92.binge.seerr.ui.users.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailShapeTest {
    @Test
    fun `ordinary and unusual valid addresses are accepted`() {
        listOf("a@b", "scott@example.com", "first.last+tag@sub.example.co.uk", "\"odd\"@example.com", "  a@b.c  ").forEach {
            assertTrue(it, it.isEmailShape())
        }
    }

    @Test
    fun `typos are rejected`() {
        listOf("", "plain", "@example.com", "scott@", "@", "a@@b.com", "a@b@c.com", "a b@example.com", "a@exa mple.com").forEach {
            assertFalse(it, it.isEmailShape())
        }
    }

    @Test
    fun `a blank email is valid on the form and a bad one blocks saving`() {
        assertTrue(GeneralSettings(email = "").valid)
        assertTrue(GeneralSettings(email = "  ").valid)
        assertTrue(GeneralSettings(email = "a@b.com").valid)
        val bad = GeneralSettings(email = "nope")
        assertFalse(bad.emailValid)
        assertFalse(bad.valid)
    }

    @Test
    fun `a bad quota still blocks saving with a good email`() {
        assertEquals(false, GeneralSettings(email = "a@b.com", movieQuotaLimit = "x").valid)
    }
}
