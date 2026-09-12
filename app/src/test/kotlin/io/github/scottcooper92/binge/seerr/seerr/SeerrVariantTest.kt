package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Test

class SeerrVariantTest {
    @Test
    fun `the fork follows the major version`() {
        assertEquals(SeerrVariant.Overseerr, SeerrVariant.fromVersion("1.33.2"))
        assertEquals(SeerrVariant.Jellyseerr, SeerrVariant.fromVersion("v2.5.0"))
        assertEquals(SeerrVariant.Seerr, SeerrVariant.fromVersion("3.0.1"))
        assertEquals(SeerrVariant.Seerr, SeerrVariant.fromVersion("4.0.0"))
    }

    @Test
    fun `a develop build brands neutrally`() {
        assertEquals(SeerrVariant.Unknown, SeerrVariant.fromVersion("develop"))
        assertEquals(SeerrVariant.Unknown, SeerrVariant.fromVersion(null))
        assertEquals("Seerr", SeerrVariant.Unknown.displayName)
    }
}
