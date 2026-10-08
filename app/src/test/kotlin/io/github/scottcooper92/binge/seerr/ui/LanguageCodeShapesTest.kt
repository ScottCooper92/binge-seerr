package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerGeneralSettings
import io.github.scottcooper92.binge.seerr.ui.users.settings.GeneralSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCodeShapesTest {
    @Test
    fun `locales of language, language-region and language-script shapes are accepted`() {
        listOf("en", "fil", "en-GB", "fr-FR", "zh-Hant", "es-419", " en-GB ").forEach {
            assertTrue(it, LanguageCodeShapes.isLocale(it))
        }
    }

    @Test
    fun `malformed locales are rejected`() {
        listOf("", "e", "english", "en_GB", "en-", "-GB", "en-G", "en GB", "en-GB-", "12", "en-GBxxxxxxxxx").forEach {
            assertFalse(it, LanguageCodeShapes.isLocale(it))
        }
    }

    @Test
    fun `a region is exactly two letters`() {
        listOf("GB", "us", " DE ").forEach { assertTrue(it, LanguageCodeShapes.isRegion(it)) }
        listOf("", "G", "GBR", "G1", "1G", "G B", "G-B").forEach { assertFalse(it, LanguageCodeShapes.isRegion(it)) }
    }

    @Test
    fun `original language is one code or a pipe or comma list of them`() {
        listOf("en", "ja", "fil", "en|fr", "en,fr", "en|fr|ja").forEach { assertTrue(it, LanguageCodeShapes.isOriginalLanguage(it)) }
        listOf("", "e", "english", "en|", "|en", "en||fr", "en fr", "en-GB", "en|f").forEach {
            assertFalse(it, LanguageCodeShapes.isOriginalLanguage(it))
        }
    }

    @Test
    fun `the server page allows blanks and rejects a wrong shape in each field`() {
        assertTrue(ServerGeneralSettings(streamingRegion = "").valid)
        assertTrue(ServerGeneralSettings(locale = "en-GB", discoverRegion = "GB", streamingRegion = "US", originalLanguage = "en|fr").valid)
        assertFalse(ServerGeneralSettings(locale = "english").valid)
        assertFalse(ServerGeneralSettings(discoverRegion = "GBR").valid)
        assertFalse(ServerGeneralSettings(streamingRegion = "U").valid)
        assertFalse(ServerGeneralSettings(originalLanguage = "en-GB").valid)
        assertFalse(ServerGeneralSettings(applicationUrl = "nope").valid)
    }

    @Test
    fun `the server page leaves a missing streaming region alone`() =
        assertTrue(ServerGeneralSettings(streamingRegion = null).streamingRegionValid)

    @Test
    fun `the user page allows blanks and rejects a wrong shape in each field`() {
        assertTrue(GeneralSettings().valid)
        assertTrue(GeneralSettings(locale = "fr-FR", region = "FR", originalLanguage = "fr").valid)
        assertFalse(GeneralSettings(locale = "en_GB").valid)
        assertFalse(GeneralSettings(region = "France").valid)
        assertFalse(GeneralSettings(originalLanguage = "en|").valid)
        // A user's "no filter" is `all`, since their blank means the server's.
        assertTrue(GeneralSettings(region = "all", streamingRegion = "all", originalLanguage = "all").valid)
    }
}
