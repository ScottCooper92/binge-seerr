package io.github.scottcooper92.binge.seerr.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import kotlinx.coroutines.CancellationException

const val USERS_PAGE_SIZE = 20

/**
 * Pages `GET user` in one [sort] into the cache. The list payload carries every field a row
 * shows, so there is no per-row lookup; a user with no name to show is dropped rather than
 * failing the page.
 */
@OptIn(ExperimentalPagingApi::class)
class UsersRemoteMediator(
    private val sort: String,
    private val api: suspend () -> SeerrApi,
    private val store: UserStore,
    private val toEntity: (SeerrUserDto, String, Int) -> UserEntity?,
) : RemoteMediator<Int, UserEntity>() {
    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, UserEntity>,
    ): MediatorResult {
        val skip =
            when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> store.nextSkip(sort) ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        return try {
            val page = api().users(take = USERS_PAGE_SIZE, skip = skip, sort = sort)
            val rows = page.results.mapIndexedNotNull { index, dto -> toEntity(dto, sort, skip + index) }
            val cursor = pageCursorAfter(skip, USERS_PAGE_SIZE, page.pageInfo.pages)
            if (loadType == LoadType.REFRESH) store.refresh(sort, rows, cursor.nextSkip) else store.append(sort, rows, cursor.nextSkip)
            MediatorResult.Success(endOfPaginationReached = cursor.endReached)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Any failure here must become a retryable error, or the pager stops for good.
            MediatorResult.Error(e)
        }
    }
}
