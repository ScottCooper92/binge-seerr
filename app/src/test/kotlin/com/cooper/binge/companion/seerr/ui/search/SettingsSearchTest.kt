package com.cooper.binge.companion.seerr.ui.search

import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.string
import com.cooper.homelab.core.foundation.UiText
import com.cooper.homelab.core.presentation.search.searchSettings
import com.cooper.homelab.core.uiandroid.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/** How a query finds an entry in the app's index, in English and in Spanish, through the shared matcher and the app's own strings. */
@RunWith(RobolectricTestRunner::class)
class SettingsSearchTest {
    private val access = SearchAccess(1, isAdmin = true, profile = SeerrServerProfile(SeerrVariant.Seerr, version = null))

    private val context get() = RuntimeEnvironment.getApplication()
    private val index get() = sharedSettingsIndex(context.resources)

    private fun text(text: UiText) = text.resolve(context)

    private fun find(query: String) = searchSettings(query, index, { it.target.available(access) }, ::text).map { text(it.label) }

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
    fun `Diagnostics is found by its name and by a keyword`() {
        assertTrue("Diagnostics" in find("diagnostics"))
        assertTrue("Diagnostics" in find("support"))
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
        val found = searchSettings("services", index, { it.target.available(member) }, ::text)
        assertEquals(emptyList<String>(), found.map { it.id })
    }

    @Test
    @Config(qualifiers = "es")
    fun `accents do not matter in Spanish`() {
        assertTrue(find("contrasena").contains(string(AdminR.string.user_settings_page_password)))
        assertTrue(find("contraseña").contains(string(AdminR.string.user_settings_page_password)))
    }

    @Test
    @Config(qualifiers = "es")
    fun `a Spanish keyword finds its page`() {
        assertTrue(string(R.string.settings_section_services) in find("descargas"))
    }

    @Test
    fun `every entry's label and keywords are written`() {
        index.forEach { entry ->
            assertTrue(text(entry.label).isNotBlank())
            assertTrue(text(entry.section).isNotBlank())
            entry.keywords?.let { assertTrue(text(it).isNotBlank()) }
        }
    }
}
