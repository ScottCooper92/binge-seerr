package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jellyseerr 2.6 to 2.x keeps the General page's blocklist settings under `blacklist` names (#874). */
class BlacklistNamesTest {
    private val jellyseerr2 =
        SeerrMainSettingsDto(hideBlacklisted = true, blacklistedTags = "12,34", blacklistedTagsLimit = 20)

    @Test
    fun `a Jellyseerr 2 server's settings fill the same form`() {
        val form = jellyseerr2.toServerGeneral(SeerrVariant.Jellyseerr, hasStreamingRegion = true)

        assertEquals(true, form.hideBlocklisted)
        assertEquals(BlocklistSettings(tags = "12,34", tagsLimit = "20"), form.blocklist)
        assertTrue(form.blacklistNames)
    }

    @Test
    fun `a Jellyseerr 2 save goes back under the names the server keeps`() {
        val form = jellyseerr2.toServerGeneral(SeerrVariant.Jellyseerr, hasStreamingRegion = true)
        val body = form.copy(blocklist = form.blocklist?.copy(tagsLimit = "30")).toBody()

        assertEquals(true, body.hideBlacklisted)
        assertNull(body.blacklistedTags)
        assertEquals(30, body.blacklistedTagsLimit)
        assertNull(body.hideBlocklisted)
        assertNull(body.blocklistedTags)
        assertNull(body.blocklistedTagsLimit)
    }

    @Test
    fun `a Seerr 3 server keeps the new names, even if a stale old one lingers`() {
        val dto =
            SeerrMainSettingsDto(hideBlocklisted = false, blocklistedTags = "56", blocklistedTagsLimit = 50, hideBlacklisted = true)
        val form = dto.toServerGeneral(SeerrVariant.Seerr, hasStreamingRegion = true)
        val body = form.toBody()

        assertFalse(form.blacklistNames)
        assertEquals(false, form.hideBlocklisted)
        assertEquals(false, body.hideBlocklisted)
        assertNull(body.blocklistedTags)
        assertNull(body.hideBlacklisted)
        assertNull(body.blacklistedTags)
    }

    @Test
    fun `a server with neither spelling has no blocklist settings`() {
        val form = SeerrMainSettingsDto().toServerGeneral(SeerrVariant.Jellyseerr, hasStreamingRegion = true)

        assertNull(form.hideBlocklisted)
        assertNull(form.blocklist)
        assertFalse(form.blacklistNames)
    }
}
