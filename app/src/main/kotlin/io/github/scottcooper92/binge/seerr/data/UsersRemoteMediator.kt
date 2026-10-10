package io.github.scottcooper92.binge.seerr.data

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto

const val USERS_PAGE_SIZE = 20

/**
 * Pages `GET user` in one [sort] into the cache. The list payload carries every field a row
 * shows, so there is no per-row lookup; a user with no name to show is dropped rather than
 * failing the page.
 */
class UsersRemoteMediator(
    private val sort: String,
    private val api: suspend () -> SeerrApi,
    store: UserStore,
    onRefresh: (rowsWritten: Int?) -> Unit = {},
    private val toEntity: (SeerrUserDto, String, Int) -> UserEntity?,
) : OffsetRemoteMediator<UserEntity>(
        listKey = sort,
        pageSize = USERS_PAGE_SIZE,
        nextSkip = store::nextSkip,
        refresh = store::refresh,
        append = store::append,
        onRefresh = onRefresh,
    ) {
    override suspend fun fetch(skip: Int): FetchedPage<UserEntity> {
        val page = api().users(take = USERS_PAGE_SIZE, skip = skip, sort = sort)
        return FetchedPage(page.results.mapIndexedNotNull { index, dto -> toEntity(dto, sort, skip + index) }, page.pageInfo.pages)
    }
}
