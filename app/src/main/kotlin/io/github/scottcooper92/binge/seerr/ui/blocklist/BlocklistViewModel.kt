package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.requests.seerrMediaType
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L

/** What the browser needs once per connection: where the list is, and what this viewer may do to it. */
private data class BlocklistScope(
    val path: String = "blocklist",
    val hasFilters: Boolean = false,
    val canManage: Boolean = false,
    val canBlockCollections: Boolean = false,
)

/**
 * The blocklist browser: one cached paged list per filter over the debounced search, the chip
 * counts, and removal keyed by TMDB id. A removal reports through [events] and bumps the list
 * version, which refreshes each filter's page in place as it is selected, so the list keeps its
 * position. Blocking a whole collection is the collection page's call, offered where the server can
 * do it.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BlocklistViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val selectedFilter = MutableStateFlow(BlocklistFilter.All)
        private val search = MutableStateFlow("")
        private val countsRefresh = MutableStateFlow(0)
        private val acting = MutableStateFlow<Set<Int>>(emptySet())
        private val listVersionState = MutableStateFlow(0)

        /**
         * The version each filter's list last refreshed at: a filter refreshes once while it trails
         * [BlocklistUiState.Ready.listVersion], so a page swiped away and back does not re-refresh a
         * current list.
         */
        private val refreshedVersions = ConcurrentHashMap<BlocklistFilter, Int>()

        private val eventFlow = MutableSharedFlow<BlocklistEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<BlocklistEvent> = eventFlow.asSharedFlow()

        /**
         * Resolved once per connection, and nothing downstream runs before it: the list's path is
         * the profile's, so a page fetched against a guessed one would be a request to the wrong place.
         *
         * `flowOn(dispatcher)` for the same reason every `launch` here takes one (#177/#370): without
         * it, this flow's own suspend calls resume on `viewModelScope`'s `Dispatchers.Main.immediate`,
         * which can outlive a cleared scope same as a plain `launch` would.
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
                    ),
                )
            }.flowOn(dispatcher)
                .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

        /** A blank query needs no debounce, so the first page is not held back. */
        private val query: Flow<String> =
            search.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }.map { it.trim() }.distinctUntilChanged()

        private val streams: Map<BlocklistFilter, Flow<PagingData<BlocklistItem>>> =
            BlocklistFilter.entries.associateWith { filter ->
                combine(query, scope) { query, scope -> query to scope }
                    .flatMapLatest { (query, scope) ->
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
            }

        fun items(filter: BlocklistFilter): Flow<PagingData<BlocklistItem>> = streams.getValue(filter)

        /** True at most once per version per filter, so a freshly composed, current page does not blank-refresh. */
        fun shouldRefresh(
            filter: BlocklistFilter,
            version: Int,
        ): Boolean {
            val last = refreshedVersions[filter] ?: 0
            if (version <= last) return false
            refreshedVersions[filter] = version
            return true
        }

        /** The previous totals stay on the chips while a refetch is in flight; a failed probe leaves that chip bare. */
        private val counts: Flow<BlocklistCounts?> =
            combine(countsRefresh, scope) { _, scope -> scope }
                .flatMapLatest { scope -> flow { emit(if (scope.hasFilters) fetchCounts(scope.path) else null) } }
                .flowOn(dispatcher)
                .onStart { emit(null) }

        val uiState: StateFlow<BlocklistUiState> =
            combine(
                selectedFilter,
                search,
                combine(counts, scope) { counts, scope -> counts to scope },
                acting,
                listVersionState,
            ) { filter, search, (counts, scope), acting, listVersion ->
                BlocklistUiState.Ready(
                    filter = filter,
                    search = search,
                    counts = counts,
                    listVersion = listVersion,
                    hasFilters = scope.hasFilters,
                    canManage = scope.canManage,
                    canBlockCollections = scope.canBlockCollections,
                    actingTmdbIds = acting,
                )
            }.stateIn(viewModelScope, SharingStarted.Lazily, BlocklistUiState.Loading)

        fun setFilter(filter: BlocklistFilter) {
            selectedFilter.value = filter
        }

        fun setSearch(query: String) {
            search.value = query
        }

        /**
         * The counts are low-velocity totals: refetched on entry and after a change, never polled.
         * The list version bumps too, so a title unblocked from its own detail page — a separate nav
         * entry with its own `ViewModelStore`, so nothing here saw that removal happen — refreshes the
         * selected page's stale row the moment the browser is back on screen, the same as a removal
         * made from this screen's own row already does.
         */
        fun setScreenVisible(visible: Boolean) {
            if (visible) {
                countsRefresh.update { it + 1 }
                listVersionState.update { it + 1 }
            }
        }

        fun remove(item: BlocklistItem) {
            if (item.tmdbId in acting.value) return
            acting.update { it + item.tmdbId }
            viewModelScope.launch(dispatcher) {
                runCatching {
                    connection.api().removeFromBlocklist(
                        connection.profile().blocklistPath,
                        item.tmdbId,
                        item.mediaType.seerrMediaType(),
                    )
                }.onSuccess {
                    countsRefresh.update { it + 1 }
                    listVersionState.update { it + 1 }
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
            viewModelScope.launch(dispatcher) {
                val profile = connection.profile()
                val permissions = runCatching { connection.authenticatedUser() }.getOrNull().toPermissions()
                if (!profile.canBlockCollections || !permissions.canManageBlocklist) return@launch
                runCatching {
                    val api = connection.api()
                    if (blocked) api.blockCollection(collectionId) else api.unblockCollection(collectionId)
                }.onSuccess {
                    countsRefresh.update { it + 1 }
                    listVersionState.update { it + 1 }
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
