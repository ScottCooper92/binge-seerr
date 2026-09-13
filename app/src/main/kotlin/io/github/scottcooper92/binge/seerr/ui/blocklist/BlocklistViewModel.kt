package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L

/** What the browser needs once per connection: where the list is, and what this viewer may do to it. */
private data class BlocklistScope(
    val path: String = "blocklist",
    val hasFilters: Boolean = false,
    val canManage: Boolean = false,
    val canBlockCollections: Boolean = false,
    val webRoot: String = "",
)

/**
 * The blocklist browser: the paged list for the selected filter and the debounced search, the
 * chip counts, and removal keyed by TMDB id. A removal reports through [events] and the screen
 * refreshes the pager in place, so the list keeps its position. Blocking a whole collection is
 * the collection page's call, offered where the server can do it.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BlocklistViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
    ) : ViewModel() {
        private val selectedFilter = MutableStateFlow(BlocklistFilter.All)
        private val search = MutableStateFlow("")
        private val countsRefresh = MutableStateFlow(0)
        private val acting = MutableStateFlow<Set<Int>>(emptySet())

        private val eventFlow = MutableSharedFlow<BlocklistEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<BlocklistEvent> = eventFlow.asSharedFlow()

        /**
         * Resolved once per connection, and nothing downstream runs before it: the list's path is
         * the profile's, so a page fetched against a guessed one would be a request to the wrong place.
         */
        private val scope: Flow<BlocklistScope> =
            flow {
                val profile = runCatching { connection.profile() }.getOrNull()
                val permissions = runCatching { connection.authenticatedUser() }.getOrNull().toPermissions()
                emit(
                    BlocklistScope(
                        path = profile?.blocklistPath ?: BlocklistScope().path,
                        hasFilters = profile?.hasBlocklistFilters == true,
                        canManage = permissions.canManageBlocklist,
                        canBlockCollections = profile?.canBlockCollections == true && permissions.canManageBlocklist,
                        webRoot = runCatching { connection.current().baseUrl }.getOrDefault(""),
                    ),
                )
            }.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

        /** A blank query needs no debounce, so the first page is not held back. */
        private val query: Flow<String> =
            search.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }.map { it.trim() }.distinctUntilChanged()

        val items: Flow<PagingData<BlocklistItem>> =
            combine(selectedFilter, query, scope) { filter, query, scope -> Triple(filter, query, scope) }
                .flatMapLatest { (filter, query, scope) ->
                    Pager(PagingConfig(pageSize = BLOCKLIST_PAGE_SIZE)) {
                        BlocklistPagingSource(
                            api = connection::api,
                            path = scope.path,
                            filter = filter,
                            search = query,
                            hydrate = titles::get,
                        )
                    }.flow
                }.cachedIn(viewModelScope)

        /** The previous totals stay on the chips while a refetch is in flight; a failed probe leaves that chip bare. */
        private val counts: Flow<BlocklistCounts?> =
            combine(countsRefresh, scope) { _, scope -> scope }
                .flatMapLatest { scope -> flow { emit(if (scope.hasFilters) fetchCounts(scope.path) else null) } }
                .onStart { emit(null) }

        val uiState: StateFlow<BlocklistUiState> =
            combine(selectedFilter, search, counts, scope, acting) { filter, search, counts, scope, acting ->
                BlocklistUiState.Ready(
                    filter = filter,
                    search = search,
                    counts = counts,
                    hasFilters = scope.hasFilters,
                    canManage = scope.canManage,
                    canBlockCollections = scope.canBlockCollections,
                    actingTmdbIds = acting,
                    webRoot = scope.webRoot,
                )
            }.stateIn(viewModelScope, SharingStarted.Lazily, BlocklistUiState.Loading)

        fun setFilter(filter: BlocklistFilter) {
            selectedFilter.value = filter
        }

        fun setSearch(query: String) {
            search.value = query
        }

        /** The counts are low-velocity totals: refetched on entry and after a change, never polled. */
        fun setScreenVisible(visible: Boolean) {
            if (visible) countsRefresh.update { it + 1 }
        }

        fun remove(item: BlocklistItem) {
            if (item.tmdbId in acting.value) return
            acting.update { it + item.tmdbId }
            viewModelScope.launch {
                runCatching { connection.api().removeFromBlocklist(connection.profile().blocklistPath, item.tmdbId) }
                    .onSuccess {
                        countsRefresh.update { it + 1 }
                        eventFlow.emit(BlocklistEvent.Removed)
                    }.onFailure { eventFlow.emit(BlocklistEvent.Failed(it.toSeerrError())) }
                acting.update { it - item.tmdbId }
            }
        }

        /** Seerr 3.2+ only; a server without it, or a viewer who cannot manage the list, is never asked. */
        fun setCollectionBlocked(
            collectionId: Int,
            blocked: Boolean,
        ) {
            viewModelScope.launch {
                val profile = connection.profile()
                val permissions = runCatching { connection.authenticatedUser() }.getOrNull().toPermissions()
                if (!profile.canBlockCollections || !permissions.canManageBlocklist) return@launch
                runCatching {
                    val api = connection.api()
                    if (blocked) api.blockCollection(collectionId) else api.unblockCollection(collectionId)
                }.onSuccess {
                    countsRefresh.update { it + 1 }
                    eventFlow.emit(BlocklistEvent.CollectionChanged(blocked))
                }.onFailure { eventFlow.emit(BlocklistEvent.Failed(it.toSeerrError())) }
            }
        }

        private suspend fun fetchCounts(path: String): BlocklistCounts =
            coroutineScope {
                val api = connection.api()

                suspend fun probe(filter: BlocklistFilter): Int? = runCatching { api.blocklistCount(path, filter) }.getOrNull()
                val all = async { probe(BlocklistFilter.All) }
                val manual = async { probe(BlocklistFilter.Manual) }
                val tagged = async { probe(BlocklistFilter.Tagged) }
                BlocklistCounts(all = all.await(), manual = manual.await(), tagged = tagged.await())
            }

        private suspend fun SeerrApi.blocklistCount(
            path: String,
            filter: BlocklistFilter,
        ): Int = blocklist(path = path, take = 1, filter = filter.apiValue).pageInfo.results
    }
