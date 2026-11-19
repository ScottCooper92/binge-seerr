package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrAddToWatchlistBody

private const val CREATED = 201

/** The signed-in user's watchlist: putting a title on it and taking one off by its TMDB id. */
internal fun DemoData.watchlistRoutes(): List<DemoRoute> =
    listOf(
        route("POST", "watchlist") { call, _ -> add(call.body) },
        route("DELETE", "watchlist", "{}") { call, (tmdbId) -> remove(tmdbId.toInt(), call.query("mediaType")) },
    )

/** Whether the signed-in user has the title on their watchlist, as the details say. */
internal fun DemoData.onWatchlist(
    mediaType: String,
    tmdbId: Int,
) = watchlist.any { it.tmdbId == tmdbId && it.mediaType == mediaType }

private fun DemoData.add(body: String): DemoAnswer {
    val asked = demoJson.decodeFromString<SeerrAddToWatchlistBody>(body)
    val title = titleFor(asked.mediaType, asked.tmdbId)
    if (watchlist.none { it == title }) watchlist += title
    return DemoAnswer(CREATED, "{}")
}

private fun DemoData.remove(
    tmdbId: Int,
    mediaType: String?,
): DemoAnswer = if (watchlist.removeAll { it.tmdbId == tmdbId && it.mediaType == mediaType }) done else demoNotFound
