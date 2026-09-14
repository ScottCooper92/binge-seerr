package io.github.scottcooper92.binge.seerr.seerr

/** Whether a request is for a series, which decides both its seasons and which *arr serves it. */
val SeerrRequestDto.isTv: Boolean get() = media.mediaType == SEERR_MEDIA_TYPE_TV

/** The servers a request of this shape may go to: Seerr keeps 4K on separate instances. */
fun List<SeerrServerDto>.forRequest(is4k: Boolean): List<SeerrServerDto> = filter { it.is4k == is4k }

/** The server a plain request would have gone to, so the picker opens on it. */
fun List<SeerrServerDto>.preferred(): SeerrServerDto? = firstOrNull { it.isDefault } ?: firstOrNull()

/**
 * Which *arr answers for a media type: Radarr serves films, Sonarr serves series, and Seerr keeps
 * one endpoint pair per service rather than one that takes the type.
 *
 * [isTv] is the caller's own reading, deliberately. The three call sites do not all derive it the
 * same way — the request page asks `!= "movie"` while the picker asks `== "tv"` — and those
 * disagree for a media type that is neither, so unifying the dispatch must not also unify that.
 */
suspend fun SeerrApi.arrServers(isTv: Boolean): List<SeerrServerDto> = if (isTv) sonarrServers() else radarrServers()

/** The chosen *arr's profiles, root folders and tags; see [arrServers] on [isTv]. */
suspend fun SeerrApi.arrServer(
    isTv: Boolean,
    id: Int,
): SeerrServerDetailsDto = if (isTv) sonarrServer(id) else radarrServer(id)
