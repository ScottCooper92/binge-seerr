package com.cooper.binge.companion.seerr.ui.blocklist

import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import kotlinx.serialization.Serializable

/**
 * One blocklisted title. [title] is the hydrated one, or the one the blocker gave when the
 * lookup failed; [tags] are the labels a tag rule blocked it under, empty for a manual block.
 * [tmdbId] keys the removal, since the server's DELETE is keyed that way rather than by [id].
 *
 * [Serializable] because it travels whole as a `BlocklistDetailRoute` navigation key: there is no
 * `GET` for a single blocklist entry, only the paged list this app already read it off of.
 */
@Serializable
data class BlocklistItem(
    val id: Int,
    val tmdbId: Int,
    val mediaType: RequestMediaType,
    val title: String?,
    val posterUrl: String?,
    val year: String?,
    val addedBy: String?,
    val addedByAvatarUrl: String? = null,
    val addedAtMillis: Long?,
    val tags: List<String>,
)
