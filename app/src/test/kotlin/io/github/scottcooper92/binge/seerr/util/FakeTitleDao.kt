package io.github.scottcooper92.binge.seerr.util

import io.github.scottcooper92.binge.seerr.data.TitleDao
import io.github.scottcooper92.binge.seerr.data.TitleEntity

/** An in-memory [TitleDao]. */
class FakeTitleDao : TitleDao {
    val rows = LinkedHashMap<Pair<String, Int>, TitleEntity>()

    override suspend fun find(
        mediaType: String,
        tmdbId: Int,
        since: Long,
    ): TitleEntity? = rows[mediaType to tmdbId]?.takeIf { it.fetchedAtMillis >= since }

    override suspend fun upsert(row: TitleEntity) {
        rows[row.mediaType to row.tmdbId] = row
    }

    override suspend fun deleteOlderThan(before: Long) {
        rows.values.removeAll { it.fetchedAtMillis < before }
    }

    override suspend fun trimTo(keep: Int) {
        val kept =
            rows.values
                .sortedByDescending { it.fetchedAtMillis }
                .take(keep)
                .map { it.mediaType to it.tmdbId }
                .toSet()
        rows.keys.retainAll(kept)
    }

    override suspend fun clear() = rows.clear()
}
