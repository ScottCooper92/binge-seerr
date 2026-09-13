package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ENTRIES = 200

/** The display fields a request or issue row needs that its payload does not carry. */
data class HydratedTitle(
    val title: String?,
    val posterUrl: String?,
    val year: String?,
)

/**
 * A title lookup per row, remembered. The request and issue lists carry only a TMDB id, so every
 * row costs a `movie/{id}` or `tv/{id}` call; a filter switch or a refresh re-lists mostly the same
 * titles, and this keeps that from re-fetching them. Only successes are kept, so a failed lookup is
 * tried again next time rather than pinned as untitled.
 */
@Singleton
class TitleCache
    @Inject
    constructor() {
        private val lock = Mutex()
        private val entries = LinkedHashMap<Pair<String, Int>, HydratedTitle>(MAX_ENTRIES, 0.75f, true)

        suspend fun get(
            api: SeerrApi,
            mediaType: String,
            tmdbId: Int,
        ): HydratedTitle? {
            val key = mediaType to tmdbId
            lock.withLock { entries[key] }?.let { return it }
            val details =
                runCatching {
                    if (mediaType == MEDIA_TYPE_MOVIE) api.movieDetails(tmdbId) else api.tvDetails(tmdbId)
                }.getOrNull() ?: return null
            val hydrated = HydratedTitle(details.displayTitle, details.posterPath?.toTmdbPosterUrl(), details.year)
            lock.withLock {
                entries[key] = hydrated
                while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
            }
            return hydrated
        }

        suspend fun clear() = lock.withLock { entries.clear() }

        private companion object {
            const val MEDIA_TYPE_MOVIE = "movie"
        }
    }
