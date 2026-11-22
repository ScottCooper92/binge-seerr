package com.cooper.binge.companion.seerr.seerr

/** TMDB's keyword for anime, the one Seerr checks a series for before it picks a Sonarr instance's anime settings. */
const val ANIME_KEYWORD_ID = 210024

/** Whether Seerr will treat this series as anime: its TMDB keywords hold [ANIME_KEYWORD_ID]. A film is never anime to Seerr. */
fun SeerrTvDetailsDto.isAnime(): Boolean = keywords.any { it.id == ANIME_KEYWORD_ID }

/** The series' anime flag, read as the series page reads it. */
suspend fun SeerrApi.isAnimeSeries(tmdbId: Int): Boolean = tvFullDetails(tmdbId).isAnime()
