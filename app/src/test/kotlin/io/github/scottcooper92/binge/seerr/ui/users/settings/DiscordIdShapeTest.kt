package io.github.scottcooper92.binge.seerr.ui.users.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordIdShapeTest {
    @Test
    fun `digits only are accepted, with surrounding spaces`() {
        listOf("0", "80351110224678912", " 80351110224678912 ").forEach { assertTrue(it, it.isDiscordIdShape()) }
    }

    @Test
    fun `anything else is rejected`() {
        listOf("scott#1234", "@scott", "<@123>", "123 456", "12a", "-1", "1.5", "١٢٣").forEach {
            assertFalse(it, it.isDiscordIdShape())
        }
    }

    @Test
    fun `a blank id is valid and a non-digit one blocks saving`() {
        assertTrue(GeneralSettings(discordId = "").valid)
        assertTrue(GeneralSettings(discordId = "123").valid)
        val bad = GeneralSettings(discordId = "scott#1234")
        assertFalse(bad.discordIdValid)
        assertFalse(bad.valid)
    }
}
