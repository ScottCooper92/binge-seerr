package com.cooper.binge.companion.seerr.ui.search

import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How a query finds an entry, in English and in Spanish, with the app's own strings. */
@RunWith(RobolectricTestRunner::class)
class SettingsSearchTest {
    private val access = SearchAccess(1, isAdmin = true, profile = SeerrServerProfile(SeerrVariant.Seerr, version = null))

    private fun find(query: String) = searchSettings(query, settingsIndex, { it.available(access) }, ::string).map { string(it.label) }

    @Test
    fun `a label's first word finds it first`() {
        assertEquals("Logs", find("logs").first())
    }

    @Test
    fun `a word that only starts another does not match inside it`() {
        assertTrue("Network" !in find("work"))
    }

    @Test
    fun `a keyword finds the page it names`() {
        assertTrue("Services" in find("radarr"))
        assertTrue("Network" in find("proxy"))
    }

    @Test
    fun `every word of the query must match`() {
        assertEquals(emptyList<String>(), find("logs radarr"))
    }

    @Test
    fun `case does not matter, and a blank query finds nothing`() {
        assertEquals(find("logs"), find("LOGS"))
        assertEquals(emptyList<String>(), find("  "))
    }

    @Test
    fun `a page the viewer cannot open is not found`() {
        val member = access.copy(userId = 7, isAdmin = false)
        val found = searchSettings("services", settingsIndex, { it.available(member) }, ::string)
        assertEquals(emptyList<SettingEntry>(), found)
    }

    @Test
    @Config(qualifiers = "es")
    fun `accents do not matter in Spanish`() {
        assertTrue(find("contrasena").contains(string(R.string.user_settings_page_password)))
        assertTrue(find("contraseña").contains(string(R.string.user_settings_page_password)))
    }

    @Test
    @Config(qualifiers = "es")
    fun `a Spanish keyword finds its page`() {
        assertTrue(string(R.string.settings_section_services) in find("descargas"))
    }

    @Test
    fun `every entry's label and keywords are written`() {
        settingsIndex.forEach { entry ->
            assertTrue(string(entry.label).isNotBlank())
            assertTrue(string(entry.section).isNotBlank())
            entry.keywords?.let { assertTrue(string(it).isNotBlank()) }
        }
    }
}
