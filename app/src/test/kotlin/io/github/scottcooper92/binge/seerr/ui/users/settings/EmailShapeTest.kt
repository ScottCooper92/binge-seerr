package io.github.scottcooper92.binge.seerr.ui.users.settings

import io.github.scottcooper92.binge.seerr.seerr.SeerrUserMainSettingsDto
import io.github.scottcooper92.binge.seerr.ui.users.isEmailShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailShapeTest {
    @Test
    fun `ordinary and unusual valid addresses are accepted`() {
        listOf("a@b", "scott@example.com", "first.last+tag@sub.example.co.uk", "\"odd\"@example.com", "  a@b.c  ", "a@b.c").forEach {
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
    fun `an email the server sent is kept valid until it is changed`() {
        val loaded = GeneralSettings(email = "alice", loadedEmail = "alice")
        assertTrue(loaded.emailValid)
        assertTrue(loaded.valid)
        val changed = loaded.copy(email = "nope")
        assertFalse(changed.emailValid)
        assertFalse(changed.valid)
    }

    @Test
    fun `a bare username from the server maps to a valid draft`() {
        val dto = SeerrUserMainSettingsDto(email = "alice")
        val draft = dto.toGeneralSettings(canEditQuotas = false, canEditEmail = false)
        assertEquals("alice", draft.loadedEmail)
        assertTrue(draft.valid)
    }

    @Test
    fun `a required email may not be cleared, unless the viewer could not have changed it`() {
        assertEquals(false, GeneralSettings(email = "", emailRequired = true, canEditEmail = true).valid)
        assertEquals(true, GeneralSettings(email = "", emailRequired = true, canEditEmail = false).valid)
        assertEquals(true, GeneralSettings(email = "", emailRequired = false, canEditEmail = true).valid)
    }

    /** The server keeps an address it has for a blank, so clearing one would save and then come back (#1020). */
    @Test
    fun `a saved address may not be blanked, since the server never clears one`() {
        val saved = GeneralSettings(email = "", loadedEmail = "a@b.com", emailRequired = false, canEditEmail = true)
        assertFalse(saved.emailValid)
        assertTrue(saved.copy(canEditEmail = false).emailValid)
    }
}
