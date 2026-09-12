package io.github.scottcooper92.binge.seerr.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.UserStore
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.hub.toHubQuota
import io.github.scottcooper92.binge.seerr.ui.requests.REQUESTS_PAGE_SIZE
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MEDIA_TYPE_MOVIE = "movie"
private const val MEDIA_TYPE_TV = "tv"

/** A carousel is a teaser; the server's own page has the rest. */
private const val CAROUSEL_LIMIT = 20

/**
 * One user as a page. The profile is the page; the quota, the watch data and the watchlist are
 * best-effort, since each is a section the server may not have or the user may not see.
 * The user's own requests are a separate paged stream.
 */
@HiltViewModel(assistedFactory = UserDetailViewModel.Factory::class)
class UserDetailViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
        private val store: UserStore,
        @Assisted private val userId: Int,
    ) : ViewModel() {
        private val state = MutableStateFlow<UserDetailUiState>(UserDetailUiState.Loading)
        val uiState: StateFlow<UserDetailUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<UserDetailEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<UserDetailEvent> = eventFlow.asSharedFlow()

        val requests: Flow<PagingData<RequestItem>> =
            Pager(PagingConfig(pageSize = REQUESTS_PAGE_SIZE)) {
                UserRequestsPagingSource(userId = userId, api = connection::api, hydrate = titles::get)
            }.flow.cachedIn(viewModelScope)

        init {
            reload()
        }

        fun reload() {
            if (state.value !is UserDetailUiState.Ready) state.value = UserDetailUiState.Loading
            viewModelScope.launch {
                runCatching { load() }
                    .onSuccess { detail -> state.value = UserDetailUiState.Ready(detail) }
                    .onFailure { failure ->
                        state.update { current -> (current as? UserDetailUiState.Ready) ?: UserDetailUiState.Error(failure.toSeerrError()) }
                    }
            }
        }

        /** Removes the user; the cached row goes too, and the page pops. */
        fun deleteUser() {
            val ready = state.value as? UserDetailUiState.Ready ?: return
            if (ready.deleting || !ready.detail.canDelete) return
            state.value = ready.copy(deleting = true)
            viewModelScope.launch {
                runCatching {
                    connection.api().deleteUser(userId)
                    store.delete(userId)
                }.onSuccess { eventFlow.emit(UserDetailEvent.UserDeleted) }
                    .onFailure { failure ->
                        state.update { current -> (current as? UserDetailUiState.Ready)?.copy(deleting = false) ?: current }
                        eventFlow.emit(UserDetailEvent.Failed(failure.toSeerrError()))
                    }
            }
        }

        private suspend fun load(): UserDetail =
            coroutineScope {
                val api = connection.api()
                val viewer = async { runCatching { connection.authenticatedUser() }.getOrNull() }
                val quota = async { runCatching { api.userQuota(userId).toHubQuota() }.getOrNull() }
                val watch = async { runCatching { api.userWatchData(userId) }.getOrNull() }
                val watchlist = async { runCatching { api.userWatchlist(userId) }.getOrNull() }
                val dto = api.user(userId)
                val item = checkNotNull(dto.toUserItem()) { "A user with nothing to show" }
                val recent =
                    watch
                        .await()
                        ?.recentlyWatched
                        ?.take(CAROUSEL_LIMIT)
                        ?.map { it.mediaType to it.tmdbId }
                        .orEmpty()
                val listed =
                    watchlist
                        .await()
                        ?.results
                        ?.take(CAROUSEL_LIMIT)
                        ?.map { it.mediaType to it.tmdbId }
                        .orEmpty()
                val cards = titled(api, recent + listed)
                val viewerDto = viewer.await()
                val permissions = viewerDto.toPermissions()
                UserDetail(
                    item = item,
                    permissions = ManageablePermission.decode(item.permissions),
                    quota = quota.await(),
                    watch = watch.await()?.let { UserWatch(playCount = it.playCount, recentlyWatched = recent.mapNotNull(cards::get)) },
                    watchlist = listed.mapNotNull(cards::get),
                    isSelf = viewerDto?.id == userId,
                    canDelete = permissions.canDelete(target = item, viewerId = viewerDto?.id),
                    webUrl = connection.current().baseUrl + "users/" + userId,
                )
            }

        /** Every carousel entry titled through the cache at once; one that will not render is dropped. */
        private suspend fun titled(
            api: SeerrApi,
            keys: List<Pair<String, Int>>,
        ): Map<Pair<String, Int>, TitleCardItem> =
            coroutineScope {
                keys
                    .distinct()
                    .map { key -> async { key to key.toCard(api) } }
                    .awaitAll()
                    .mapNotNull { (key, card) -> card?.let { key to it } }
                    .toMap()
            }

        private suspend fun Pair<String, Int>.toCard(api: SeerrApi): TitleCardItem? {
            val mediaType =
                when (first) {
                    MEDIA_TYPE_MOVIE -> RequestMediaType.Movie
                    MEDIA_TYPE_TV -> RequestMediaType.Tv
                    else -> return null
                }
            val details = titles.get(api, first, second)
            return TitleCardItem(tmdbId = second, mediaType = mediaType, title = details?.title, posterUrl = details?.posterUrl)
        }

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): UserDetailViewModel
        }
    }

/** The server's own guard: a manager, never the owner or oneself, and another admin only by the owner. */
private fun SeerrPermissions.canDelete(
    target: UserItem,
    viewerId: Int?,
): Boolean {
    if (!canManageUsers || viewerId == null) return false
    if (target.id == OWNER_USER_ID || target.id == viewerId) return false
    return !target.isAdmin || viewerId == OWNER_USER_ID
}
