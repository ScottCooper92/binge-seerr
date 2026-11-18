package com.cooper.binge.companion.seerr.discover.collection

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.toTitleResult
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [CollectionRepository] over the Seerr API. [io] is the dispatcher the call runs on; it has no default. */
class SeerrCollectionRepository(
    private val api: suspend () -> SeerrApi,
    private val viewer: suspend () -> SeerrUserDto,
    private val profile: suspend () -> SeerrServerProfile,
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
