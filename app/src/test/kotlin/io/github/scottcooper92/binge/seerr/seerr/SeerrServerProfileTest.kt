package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MEDIA_SERVER_JELLYFIN = 2

class SeerrServerProfileTest {
    @Test
    fun `a release version parses with or without its prefix, and a development build has none`() {
        assertEquals(SeerrVersion(2, 7, 3), SeerrVersion.parse("2.7.3"))
        assertEquals(SeerrVersion(1, 33, 0), SeerrVersion.parse("v1.33.0"))
        assertEquals(SeerrVersion(3, 4, 0), SeerrVersion.parse("3.4"))
        assertNull(SeerrVersion.parse("develop-abc123"))
        assertNull(SeerrVersion.parse("local"))
        assertNull(SeerrVersion.parse(null))
        assertTrue(SeerrVersion(3, 0, 0) > SeerrVersion(2, 7, 3))
    }

    @Test
    fun `overseerr has no blocklist and gates issues on its own releases`() {
        val old = profile("1.27.0")
        val current = profile("1.33.2")

        assertFalse(old.hasBlocklist)
        assertFalse(current.hasBlocklist)
        assertFalse(old.hasIssues)
        assertTrue(current.hasIssues)
        assertFalse(old.hasCounts)
        assertTrue(current.hasCounts)
        assertFalse(current.hasOverrideRules)
        assertFalse(current.hasQuickConnect)
    }

    @Test
    fun `the jellyseerr lineage grows features by version, and the blocklist path renames at 3`() {
        val jellyseerr = profile("2.1.0")
        val later = profile("2.6.0")
        val seerr = profile("3.4.1")

        assertTrue(jellyseerr.hasBlocklist)
        assertEquals("blacklist", jellyseerr.blocklistPath)
        assertFalse(jellyseerr.hasOverrideRules)
        assertTrue(later.hasOverrideRules)
        assertTrue(later.hasNetworkSettings)
        assertFalse(later.hasQuickConnect)
        assertEquals("blocklist", seerr.blocklistPath)
        assertTrue(seerr.canBlockCollections)
        assertTrue(seerr.hasQuickConnect)
        assertTrue(seerr.hasIssues)
    }

    @Test
    fun `a development build takes its lineage from the public settings, at its latest`() {
        val jellyseerrDevelop =
            SeerrServerProfile.from(
                SeerrStatusDto(version = "develop-abc"),
                SeerrPublicSettings(mediaServerType = MEDIA_SERVER_JELLYFIN),
            )
        val overseerrDevelop = SeerrServerProfile.from(SeerrStatusDto(version = "develop-abc"), SeerrPublicSettings())

        assertEquals(SeerrVariant.Seerr, jellyseerrDevelop.variant)
        assertNull(jellyseerrDevelop.version)
        assertTrue(jellyseerrDevelop.hasQuickConnect)
        assertEquals(SeerrVariant.Overseerr, overseerrDevelop.variant)
        assertTrue(overseerrDevelop.hasIssues)
        assertFalse(overseerrDevelop.hasBlocklist)
    }

    @Test
    fun `sign-in modes follow the lineage, the media server and what the admin turned on`() {
        val overseerr = profile("1.33.0", SeerrPublicSettings(localLogin = false))
        val jellyfin = profile("3.4.0", SeerrPublicSettings(mediaServerType = MEDIA_SERVER_JELLYFIN))
        val plexJellyseerr = profile("2.7.0", SeerrPublicSettings(mediaServerType = 1))
        val locked =
            profile("2.7.0", SeerrPublicSettings(mediaServerType = MEDIA_SERVER_JELLYFIN, mediaServerLogin = false, localLogin = false))

        assertEquals(setOf(SeerrSignInMode.ApiKey, SeerrSignInMode.Plex), overseerr.signInModes)
        assertEquals(
            setOf(SeerrSignInMode.ApiKey, SeerrSignInMode.Local, SeerrSignInMode.Jellyfin, SeerrSignInMode.QuickConnect),
            jellyfin.signInModes,
        )
        assertEquals(setOf(SeerrSignInMode.ApiKey, SeerrSignInMode.Local, SeerrSignInMode.Plex), plexJellyseerr.signInModes)
        assertEquals(setOf(SeerrSignInMode.ApiKey), locked.signInModes)
    }

    @Test
    fun `the update fields ride along from status`() {
        val profile =
            SeerrServerProfile.from(
                SeerrStatusDto(version = "2.7.0", commitTag = "abc", updateAvailable = true, commitsBehind = 4),
                SeerrPublicSettings(),
            )

        assertEquals("abc", profile.commitTag)
        assertTrue(profile.updateAvailable)
        assertEquals(4, profile.commitsBehind)
    }

    private fun profile(
        version: String,
        settings: SeerrPublicSettings = SeerrPublicSettings(),
    ): SeerrServerProfile = SeerrServerProfile.from(SeerrStatusDto(version = version), settings)
}
