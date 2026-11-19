package com.cooper.binge.companion.seerr.discover.collection

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.seedRows
import com.cooper.binge.companion.seerr.discover.toAvailability
import com.cooper.binge.companion.seerr.discover.toTitleResult
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [CollectionRepository] over the Seerr API. [io] is the dispatcher the call runs on; it has no default. */
class SeerrCollectionRepository(
    private val api: suspend () -> SeerrApi,
    private val viewer: suspend () -> SeerrUserDto,
    private val profile: suspend () -> SeerrServerProfile,
    private val io: CoroutineDispatcher,
    private val statuses: TitleStatusStore = TitleStatusStore(),
) : CollectionRepository {
    override suspend fun collection(id: Int): CollectionDetails =
        withContext(io) {
            val read = statuses.beginRead()
            val dto = api().collection(id)
            statuses.seedRows(read, dto.parts.map { Triple(TitleKind.Movie, it.tmdbId ?: it.id, it.mediaInfo) })
            CollectionDetails(
                id = dto.id,
                name = dto.name,
                overview = dto.overview?.takeIf { it.isNotBlank() },
                backdropUrl = (dto.backdropPath ?: dto.posterPath)?.toTmdbBackdropUrl(),
                posterUrl = dto.posterPath?.toTmdbPosterUrl(),
                parts =
                    dto.parts
                        .mapNotNull { it.toTitleResult(TitleKind.Movie) }
                        .sortedWith(compareBy(nullsLast()) { it.year }),
                parts4k =
                    dto.parts
                        .mapNotNull { part ->
                            (part.tmdbId ?: part.id)?.let { it to part.mediaInfo?.status4k.toAvailability() }
                        }.toMap(),
            )
        }

    override suspend fun mayRequest(): Boolean = withContext(io) { viewer().toPermissions().canRequestMovie }

    override suspend fun mayBlock(): Boolean =
        withContext(io) { profile().canBlockCollections && viewer().toPermissions().canManageBlocklist }

    override suspend fun serverProfile(): SeerrServerProfile? = attempt { profile() }.getOrNull()

    override suspend fun setBlocked(
        id: Int,
        blocked: Boolean,
    ) = withContext(io) {
        val api = api()
        if (blocked) api.blockCollection(id) else api.unblockCollection(id)
    }
}
