package io.github.scottcooper92.binge.seerr.seerr

/** The servers a request of this shape may go to: Seerr keeps 4K on separate instances. */
fun List<SeerrServerDto>.forRequest(is4k: Boolean): List<SeerrServerDto> = filter { it.is4k == is4k }

/** The server a plain request would have gone to, so the picker opens on it. */
fun List<SeerrServerDto>.preferred(): SeerrServerDto? = firstOrNull { it.isDefault } ?: firstOrNull()
