package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import org.junit.Assert.assertEquals
import org.junit.Test

/** The hub's first frame is read from the cached profile, so a server that will not show Browse all never gets its place (#2286). */
class HubFirstFrameTest {
    private fun frame(profile: SeerrServerProfile?) = SeerrHubFirstFrames { profile }.read()

    @Test
    fun `with no cached profile both places are kept`() = assertEquals(HubFirstFrame(heroPlace = true, browseAll = true), frame(null))

    @Test
    fun `an Overseerr before 1_32 cannot filter, so Browse all has no place`() =
        assertEquals(
            HubFirstFrame(heroPlace = true, browseAll = false),
            frame(SeerrServerProfile(variant = SeerrVariant.Overseerr, version = SeerrVersion(1, 31, 0))),
        )

    @Test
    fun `an Overseerr from 1_32 keeps both places`() =
        assertEquals(
            HubFirstFrame(heroPlace = true, browseAll = true),
            frame(SeerrServerProfile(variant = SeerrVariant.Overseerr, version = SeerrVersion(1, 32, 0))),
        )
}
