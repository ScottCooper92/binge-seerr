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
        assertFalse(current.hasDeleteMediaFiles)
        assertFalse(profile("1.28.0").hasWatchData)
        assertTrue(current.hasWatchData)
        assertFalse(current.hasStreamingRegion)
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
        assertFalse(profile("1.4.0").hasDeleteMediaFiles)
        assertTrue(jellyseerr.hasDeleteMediaFiles)
        assertTrue(jellyseerr.hasWatchData)
        // The one region split in two at 2.2, not at Jellyseerr's first release (#1012).
        assertFalse(jellyseerr.hasStreamingRegion)
        assertTrue(profile("2.2.0").hasStreamingRegion)
        assertTrue(seerr.hasStreamingRegion)
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
    fun `a 1x with a media server type is Jellyseerr at its version, and one without is Overseerr`() {
        val jellyseerr = profile("1.9.2", SeerrPublicSettings(mediaServerType = MEDIA_SERVER_JELLYFIN))
        val overseerr = profile("1.33.2", SeerrPublicSettings())

        assertEquals(SeerrVariant.Jellyseerr, jellyseerr.variant)
        assertEquals(SeerrVersion(1, 9, 2), jellyseerr.version)
        assertTrue(jellyseerr.complete)
        // Its own releases' features, which the old Overseerr reading made unreachable for any real 1.x.
        assertTrue(jellyseerr.hasDeleteMediaFiles)
        assertTrue(jellyseerr.hasPushoverSounds)
        assertTrue(jellyseerr.hasGotify)
        assertTrue(jellyseerr.hasWatchData)
        // What arrived in later majors stays hidden on it, and LunaSea is Overseerr's alone.
        assertFalse(jellyseerr.hasBlocklist)
        assertFalse(jellyseerr.hasOverrideRules)
        assertFalse(jellyseerr.hasNtfy)
        assertFalse(jellyseerr.hasLunaSea)
        assertEquals(SeerrMediaServer.Jellyfin, jellyseerr.mediaServer)

        assertEquals(SeerrVariant.Overseerr, overseerr.variant)
        assertFalse(overseerr.hasDeleteMediaFiles)
        assertTrue(overseerr.hasLunaSea)
        assertEquals(SeerrMediaServer.Plex, overseerr.mediaServer)
    }

    @Test
    fun `a 1x whose settings failed reads as Overseerr, and is incomplete so the next read tries again`() {
        val profile = SeerrServerProfile.from(SeerrStatusDto(version = "1.9.2"), settings = null)

        assertEquals(SeerrVariant.Overseerr, profile.variant)
        assertEquals(SeerrVersion(1, 9, 2), profile.version)
        assertFalse(profile.complete)
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
    fun `a lineage that could not be read hides nothing and is not taken for Overseerr`() {
        val unknown = SeerrServerProfile.unknown(SeerrVariant.Unknown)

        assertFalse(unknown.complete)
        assertTrue(unknown.hasBlocklist)
        assertTrue(unknown.hasOverrideRules)
        assertTrue(unknown.hasQuickConnect)
        assertTrue(unknown.hasIssues)
        assertTrue(unknown.hasGotify)
        assertFalse(unknown.hasLunaSea)
    }

    @Test
    fun `a development build whose settings failed keeps the recorded lineage and is incomplete`() {
        val develop = SeerrStatusDto(version = "develop-abc")

        val recorded = SeerrServerProfile.from(develop, settings = null, fallback = SeerrVariant.Seerr)
        val unrecorded = SeerrServerProfile.from(develop, settings = null)

        assertEquals(SeerrVariant.Seerr, recorded.variant)
        assertFalse(recorded.complete)
        assertEquals(SeerrVariant.Unknown, unrecorded.variant)
        assertTrue(unrecorded.hasBlocklist)
        assertTrue(SeerrServerProfile.from(develop, SeerrPublicSettings()).complete)
    }

    @Test
    fun `a media server type that was not read is unknown, except on Overseerr where it is always Plex`() {
        assertEquals(SeerrMediaServer.Plex, profile("1.33.0").mediaServer)
        assertEquals(SeerrMediaServer.Unknown, profile("3.4.0").mediaServer)
        assertEquals(SeerrMediaServer.Unknown, SeerrServerProfile.unknown(SeerrVariant.Unknown).mediaServer)
        assertEquals(SeerrMediaServer.Jellyfin, profile("3.4.0", SeerrPublicSettings(mediaServerType = MEDIA_SERVER_JELLYFIN)).mediaServer)
        assertEquals(SeerrMediaServer.Unknown, SeerrMediaServer.fromCode(null))
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
