package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.discover.TitleStatusMap
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode

private fun RequestMediaType.titleType(): TitleType =
    when (this) {
        RequestMediaType.Movie -> TitleType.Movie
        RequestMediaType.Tv -> TitleType.Tv
    }

/**
 * The row's title blocked or unblocked since the row was read, as the cards of the discovery lists show it (#2354). Only that
 * one change is taken from the store: the row's own request and its download are the server's to say, and a status the store
 * holds from an older read of the title must not undo what the list read later.
 */
fun RequestItem.withBlockFrom(statuses: TitleStatusMap): RequestItem {
    val titleKey = TitleKey(mediaType.titleType(), tmdbId)
    val known = statuses[titleKey] ?: return this
    val held = known.statusOf(is4k)
    val blockedNow = held == SeerrMediaStatusCode.Blocklisted && mediaStatus != SeerrMediaStatusCode.Blocklisted
    val unblockedNow = held == null && mediaStatus == SeerrMediaStatusCode.Blocklisted && !is4k
    return if (blockedNow || unblockedNow) copy(mediaStatus = held) else this
}
