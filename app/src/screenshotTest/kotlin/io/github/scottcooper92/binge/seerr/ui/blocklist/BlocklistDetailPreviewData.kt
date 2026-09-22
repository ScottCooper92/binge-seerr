package io.github.scottcooper92.binge.seerr.ui.blocklist

import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

private const val TMDB_ID = 1396
private const val ADDED_AT_MILLIS = 1_749_000_000_000L

/** A manager's own page: tags, an overview, and the Unblock footer. */
internal fun manageableBlocklistDetail(): BlocklistDetailUiState =
    BlocklistDetailUiState(
        item =
            BlocklistItem(
                id = 11,
                tmdbId = TMDB_ID,
                mediaType = RequestMediaType.Tv,
                title = "Breaking Bad",
                posterUrl = null,
                year = "2008",
                addedBy = "Ada",
                addedAtMillis = ADDED_AT_MILLIS,
                tags = listOf("Adult content", "Violence"),
            ),
        canManage = true,
        backdropUrl = null,
        overview =
            "A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing to secure his " +
                "family's future, and the distance between the man who began it and the one still doing it stops " +
                "being something anyone can measure.",
        webUrl = "https://seerr.example/tv/1396",
    )

/** A viewer with `VIEW_BLOCKLIST` only: no Unblock footer, and nothing to say who blocked it or when. */
internal fun readOnlyBlocklistDetail(): BlocklistDetailUiState =
    BlocklistDetailUiState(
        item =
            BlocklistItem(
                id = 12,
                tmdbId = 550,
                mediaType = RequestMediaType.Movie,
                title = "Fight Club",
                posterUrl = null,
                year = "1999",
                addedBy = null,
                addedAtMillis = null,
                tags = emptyList(),
            ),
        canManage = false,
        backdropUrl = null,
        overview = null,
        webUrl = "https://seerr.example/movie/550",
    )
