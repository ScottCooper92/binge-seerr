package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.TitleStatuses
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_TV
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto

/** Records what a request or media list says of its titles, both versions, for [read] to order as any other server read. */
internal fun TitleStatusStore.seedMedia(
    read: Long,
    media: List<SeerrRequestMediaDto>,
) {
    media.forEach { row ->
        val type =
            when (row.mediaType) {
                SEERR_MEDIA_TYPE_MOVIE -> TitleType.Movie
                SEERR_MEDIA_TYPE_TV -> TitleType.Tv
                else -> null
            } ?: return@forEach
        seed(TitleKey(type, row.tmdbId), TitleStatuses(row.status, row.status4k, row.id), read)
    }
}
