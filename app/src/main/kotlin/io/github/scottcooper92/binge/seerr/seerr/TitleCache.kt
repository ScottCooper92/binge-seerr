package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.data.TitleDao
import io.github.scottcooper92.binge.seerr.data.TitleEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ENTRIES = 200
private const val MAX_PERSISTED = 2_000
private const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000

/** The display fields a request or issue row needs that its payload does not carry. */
data class HydratedTitle(
    val title: String?,
    val posterUrl: String?,
    val year: String?,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val certification: String? = null,
)

/**
 * A title lookup per row, remembered. The request and issue lists carry only a TMDB id, so every
 * row costs a `movie/{id}` or `tv/{id}` call; a filter switch or a refresh re-lists mostly the same
 * titles, and this keeps that from re-fetching them. Behind the in-memory layer is [TitleDao], so a
 * cold start does not re-fetch every row either; persisted titles expire after 30 days and the table
 * is capped, both applied once per process on the first write. Only successes are kept, so a failed lookup is
 * tried again next time rather than pinned as untitled.
 */
@Singleton
class TitleCache
    internal constructor(
        private val dao: TitleDao,
        private val now: () -> Long,
    ) {
        @Inject
        constructor(dao: TitleDao) : this(dao, System::currentTimeMillis)

        private val lock = Mutex()
        private val entries = LinkedHashMap<Pair<String, Int>, HydratedTitle>(MAX_ENTRIES, 0.75f, true)
        private var pruned = false

        suspend fun get(
            api: SeerrApi,
            mediaType: String,
            tmdbId: Int,
        ): HydratedTitle? {
            val key = mediaType to tmdbId
            val known = lock.withLock { entries[key] } ?: persisted(mediaType, tmdbId)?.also { remember(key, it) }
            return known ?: fetch(api, mediaType, tmdbId)?.also {
                remember(key, it)
                persist(mediaType, tmdbId, it)
            }
        }

        private suspend fun fetch(
            api: SeerrApi,
            mediaType: String,
            tmdbId: Int,
        ): HydratedTitle? {
            val details =
                runCatching {
                    if (mediaType == SEERR_MEDIA_TYPE_MOVIE) api.movieDetails(tmdbId) else api.tvDetails(tmdbId)
                }.getOrNull() ?: return null
            return HydratedTitle(
                details.displayTitle,
                details.posterPath?.toTmdbPosterUrl(),
                details.year,
                details.backdropPath?.toTmdbBackdropUrl(),
                details.overview,
                details.certification(Locale.getDefault().country),
            )
        }

        suspend fun clear() {
            lock.withLock { entries.clear() }
            runCatching { dao.clear() }
        }

        private suspend fun remember(
            key: Pair<String, Int>,
            hydrated: HydratedTitle,
        ) = lock.withLock {
            entries[key] = hydrated
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }

        private suspend fun persisted(
            mediaType: String,
            tmdbId: Int,
        ): HydratedTitle? =
            runCatching { dao.find(mediaType, tmdbId, now() - MAX_AGE_MILLIS) }
                .getOrNull()
                ?.let { HydratedTitle(it.title, it.posterUrl, it.year, it.backdropUrl, it.overview, it.certification) }

        private suspend fun persist(
            mediaType: String,
            tmdbId: Int,
            hydrated: HydratedTitle,
        ) {
            val first = lock.withLock { !pruned.also { pruned = true } }
            runCatching {
                if (first) {
                    dao.deleteOlderThan(now() - MAX_AGE_MILLIS)
                    dao.trimTo(MAX_PERSISTED)
                }
                dao.upsert(
                    TitleEntity(
                        mediaType,
                        tmdbId,
                        hydrated.title,
                        hydrated.posterUrl,
                        hydrated.year,
                        now(),
                        hydrated.backdropUrl,
                        hydrated.overview,
                        hydrated.certification,
                    ),
                )
            }
        }
    }
