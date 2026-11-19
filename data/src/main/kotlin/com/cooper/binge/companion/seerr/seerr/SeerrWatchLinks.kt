package com.cooper.binge.companion.seerr.seerr

/** The first of the standard version's links that can be a real site (see [isOpenableSite]); the contract carries no other kind. */
fun SeerrMediaInfoDto.standardWatchLink(): String? = firstOpenable(mediaUrl, jellyfinMediaUrl, plexUrl)

/** The first of the 4K version's links that can be a real site. */
fun SeerrMediaInfoDto.fourKWatchLink(): String? = firstOpenable(mediaUrl4k, plexUrl4k)

/**
 * The title's one watch link for a page: the standard version's before the 4K one's, so a title held only in 4K still opens. It is
 * built from the two per-version links the Service sends, so the page and the Service cannot disagree about what a link is.
 */
fun SeerrMediaInfoDto.watchLink(): String? = standardWatchLink() ?: fourKWatchLink()

private fun firstOpenable(vararg candidates: String?): String? = candidates.firstOrNull { !it.isNullOrBlank() && isOpenableSite(it) }
