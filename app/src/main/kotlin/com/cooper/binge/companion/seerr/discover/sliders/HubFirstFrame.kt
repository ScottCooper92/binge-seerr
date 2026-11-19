package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.grid.refinableOn
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile

/** What a hub's first frame keeps room for: the hero's place and the Browse all plate. Both are there until the server says they will not be. */
data class HubFirstFrame(
    val heroPlace: Boolean = true,
    val browseAll: Boolean = true,
)

/** What the hub can know about its first frame before the server has answered. */
fun interface HubFirstFrames {
    fun read(): HubFirstFrame
}

/**
 * [HubFirstFrames] from the connection's cached profile, so a launch after the first leaves out what the server will not show and the
 * hub does not move when its rows arrive. With no cached profile, a first connection, both places are kept.
 */
class SeerrHubFirstFrames(
    private val cachedProfile: () -> SeerrServerProfile?,
) : HubFirstFrames {
    override fun read(): HubFirstFrame =
        cachedProfile()?.let { profile ->
            HubFirstFrame(
                heroPlace = TitleSource.Trending.supportedBy(profile),
                browseAll = GridPreset.Popular.refinableOn(profile),
            )
        } ?: HubFirstFrame()
}
