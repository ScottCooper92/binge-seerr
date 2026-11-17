package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSlider
import com.cooper.binge.companion.seerr.ui.settings.server.SliderType

/** The built-in kinds in the order a server starts with them, all on. */
private val DEFAULT_ORDER =
    listOf(
        SliderType.RecentlyAdded,
        SliderType.RecentRequests,
        SliderType.Watchlist,
        SliderType.Trending,
        SliderType.PopularMovies,
        SliderType.MovieGenres,
        SliderType.UpcomingMovies,
        SliderType.Studios,
        SliderType.PopularTv,
        SliderType.TvGenres,
        SliderType.UpcomingTv,
        SliderType.Networks,
    )

/**
 * The sliders of a server with no slider API, which shows the web client's own default set. Their ids are the negative of
 * their kind's number, so no one mistakes them for rows the server holds.
 */
fun defaultSliders(): List<DiscoverSlider> =
    DEFAULT_ORDER.map { type ->
        DiscoverSlider(id = -type.code, typeCode = type.code, title = null, builtIn = true, enabled = true, data = null)
    }
