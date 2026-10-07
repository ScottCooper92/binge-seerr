package io.github.scottcooper92.binge.seerr.ui.settings.server

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguagePickerTest {
    private val entries = listOf(ListEntry("fr", "French"), ListEntry("de", "German"), ListEntry("ja", "Japanese"))

    @Test
    fun `the saved value splits on the server's separator`() {
        assertEquals(listOf("en", "ja"), " en | ja |".languageCodes())
        assertEquals(emptyList<String>(), "".languageCodes())
    }

    @Test
    fun `chosen languages lead, then the rest by name, and a saved code the list lacks stays`() {
        val list = languageChecklist(entries, initial = listOf("ja", "xx"), filter = "")

        assertEquals(listOf("ja", "xx", "fr", "de"), list.map { it.first })
    }

    @Test
    fun `the filter matches a name or a whole code`() {
        assertEquals(listOf("fr"), languageChecklist(entries, emptyList(), "fren").map { it.first })
        assertEquals(listOf("de"), languageChecklist(entries, emptyList(), " DE ").map { it.first })
    }

    @Test
    fun `a code the device cannot name falls back to tmdb's name, then the code`() {
        assertEquals("Klingon list", languageName("zzz", "Klingon list"))
        assertEquals("zzz", languageName("zzz"))
    }
}
