package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.seerr.SeerrAddToWatchlistBody
import com.cooper.binge.companion.seerr.seerr.SeerrApi

/** Puts a title on the signed-in user's watchlist, or takes it off. Adding requests nothing. */
internal suspend fun setOnWatchlist(
    api: SeerrApi,
    tmdbId: Int,
    title: String,
    mediaType: String,
    onList: Boolean,
) = if (onList) api.addToWatchlist(SeerrAddToWatchlistBody(tmdbId, mediaType, title)) else api.removeFromWatchlist(tmdbId, mediaType)
