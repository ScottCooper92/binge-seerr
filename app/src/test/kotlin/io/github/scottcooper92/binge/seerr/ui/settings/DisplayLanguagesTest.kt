package io.github.scottcooper92.binge.seerr.ui.settings

import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayLanguagesTest {
    @Test
    fun `overseerr offers five fewer languages than the jellyseerr lineage`() {
        val lineage = DisplayLanguages.codesFor(SeerrVariant.Jellyseerr)
        val overseerr = DisplayLanguages.codesFor(SeerrVariant.Overseerr)

        assertEquals(37, lineage.size)
        assertEquals(setOf("es-MX", "et", "lb", "tr", "vi"), lineage.toSet() - overseerr.toSet())
        assertEquals(lineage, DisplayLanguages.codesFor(SeerrVariant.Unknown))
    }

    @Test
    fun `a saved value the list lacks comes first under its code`() {
        val choices = DisplayLanguages.choices(SeerrVariant.Overseerr, "vi")

        assertEquals("vi" to "vi", choices.first())
        assertEquals(33, choices.size)
    }

    @Test
    fun `a known value is not repeated, and names are each language's own`() {
        val choices = DisplayLanguages.choices(SeerrVariant.Seerr, "de")

        assertEquals(1, choices.count { it.first == "de" })
        assertEquals("Deutsch", DisplayLanguages.nativeName("de"))
    }

    @Test
    fun `regions start with everywhere and keep an unknown saved code`() {
        val choices = Regions.choices(listOf("GB", "", "GB", "FR"), current = "XQ", allLabel = "All regions")

        assertEquals("" to "All regions", choices[0])
        assertEquals("XQ" to "XQ", choices[1])
        assertEquals(listOf("FR", "GB"), choices.drop(2).map { it.first })
    }

    @Test
    fun `codes for countries that no longer exist are left out, unless one is the current value`() {
        // As TMDB lists them: Serbia beside Serbia and Montenegro, and Yugoslavia, which Android names "Serbia" too (#932).
        val codes = listOf("RS", "CS", "YU", "ME", "ZR", "CD")

        assertEquals(listOf("CD", "ME", "RS"), Regions.choices(codes, current = "", allLabel = "All").drop(1).map { it.first })
        assertEquals("CS" to Regions.name("CS"), Regions.choices(codes, current = "CS", allLabel = "All")[1])
    }

    @Test
    fun `a code the device cannot name falls back to itself`() {
        assertEquals("United Kingdom", Regions.name("GB"))
        assertEquals("not-a-region", Regions.name("not-a-region"))
    }
}
