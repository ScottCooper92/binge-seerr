package io.github.scottcooper92.binge.seerr.ui.blocklist

import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

/**
 * Fixed and kept far past the 30-day relative-date window `formatRelativeOrAbsolute` switches on, so
 * `BlocklistRow`'s blocked-at date always renders the same absolute date — it has no `now` override of
 * its own to pin the way `IssueRow`/`RequestRow` do.
 */
private const val BLOCKED_AT_MILLIS = 1_759_000_000_000L
private const val TV_TMDB_ID = 95_396

/** Several tag labels crowding the row's `FlowRow` line, alongside a long title and blocker name (#419). */
internal fun manyTagsBlocklistItem(): BlocklistItem =
    BlocklistItem(
        id = 1,
        tmdbId = TV_TMDB_ID,
        mediaType = RequestMediaType.Tv,
        title = "The Marvelous Mrs. Maisel",
        posterUrl = null,
        year = "2017",
        addedBy = "Alexandria Fitzgerald-Whitmore",
        addedAtMillis = BLOCKED_AT_MILLIS,
        tags = listOf("Not Available", "Adult Content", "Anime", "Sports", "4K Discovery"),
    )

/** No manage permission: no trailing "Unblock" button, so the content column takes the row's full width. */
internal fun readOnlyBlocklistItem(): BlocklistItem = manyTagsBlocklistItem().copy(id = 2, addedBy = null, tags = listOf("Manual"))
