package io.github.scottcooper92.binge.seerr.ui.settings.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePickerTest {
    private val entries = listOf(ListEntry("fr", "French"), ListEntry("de", "German"), ListEntry("ja", "Japanese"))

    @Test
    fun `the saved value splits on the server's separator`() {
        assertEquals(listOf("en", "ja"), " en | ja |".languageCodes())
        assertEquals(emptyList<String>(), "".languageCodes())
    }

    @Test
    fun `the checklist is by name, and a saved code the list lacks stays`() {
        val list = languageChecklist(entries, initial = listOf("ja", "xx"))

        assertEquals(listOf("fr", "de", "ja", "xx"), list.map { it.first })
    }

    @Test
    fun `a code the device cannot name falls back to tmdb's name, then the code`() {
        assertEquals("Klingon list", languageName("zzz", "Klingon list"))
        assertEquals("zzz", languageName("zzz"))
    }

    @Test
    fun `typed codes are usable when blank or every code is two or three letters`() {
        assertTrue(languageEntryUsable(""))
        assertTrue(languageEntryUsable(" en | ja |"))
        assertFalse(languageEntryUsable("english"))
        assertFalse(languageEntryUsable("en-US"))
        assertFalse(languageEntryUsable("en fr"))
    }
}
