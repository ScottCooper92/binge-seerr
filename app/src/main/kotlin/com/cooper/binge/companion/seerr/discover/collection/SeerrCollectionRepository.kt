package com.cooper.binge.companion.seerr.discover.collection

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.toTitleResult
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [CollectionRepository] over the Seerr API. [io] is the dispatcher the call runs on; it has no default. */
class SeerrCollectionRepository(
    private val api: suspend () -> SeerrApi,
    private val io: CoroutineDispatcher,
) : CollectionRepository {
    override suspend fun collection(id: Int): CollectionDetails =
        withContext(io) {
            val dto = api().collection(id)
            CollectionDetails(
                id = dto.id,
                name = dto.name,
                overview = dto.overview?.takeIf { it.isNotBlank() },
                backdropUrl = (dto.backdropPath ?: dto.posterPath)?.toTmdbBackdropUrl(),
                parts =
                    dto.parts
                        .mapNotNull { it.toTitleResult(TitleKind.Movie) }
                        .sortedWith(compareBy(nullsLast()) { it.year }),
            )
        }
}
