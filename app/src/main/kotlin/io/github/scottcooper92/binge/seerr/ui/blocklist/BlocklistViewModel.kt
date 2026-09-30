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
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpCrashBreadcrumbs
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

/** What one arrival's reads returned; null is a read that failed, and [viewerError] says why the viewer's did. */
private class ScopeRead(
    val profile: SeerrServerProfile?,
    val viewer: SeerrUserDto?,
    val viewerError: SeerrError?,
)

/** Where resolving [BlocklistScope] stands: in flight, done, or failed with why, so a failure is a state rather than a skeleton that never ends. */
private sealed interface ScopeState {
    data object Resolving : ScopeState

    data class Resolved(
        val scope: BlocklistScope,
    ) : ScopeState

    data class Failed(
        val error: SeerrError,
    ) : ScopeState
}

/** This scope with [read] applied: each part the read answered is replaced, and one it did not keeps its last value. */
private fun BlocklistScope.readWith(read: ScopeRead): BlocklistScope {
    val canManage = read.viewer?.toPermissions()?.canManageBlocklist ?: canManage
    return BlocklistScope(
        path = read.profile?.blocklistPath ?: path,
        hasFilters = read.profile?.hasBlocklistFilters ?: hasFilters,
        canManage = canManage,
        canBlockCollections = (read.profile?.canBlockCollections ?: canBlockCollections) && canManage,
    )
}

/**
 * The state after [read]. A first read that could not identify the viewer is [ScopeState.Failed]:
 * whether they may manage the list is unknown, and a guessed answer would offer or hide removal on
 * no evidence. Once a scope has resolved, a failed re-read keeps it.
 */
private fun ScopeState.readWith(read: ScopeRead): ScopeState {
    val previous = (this as? ScopeState.Resolved)?.scope
    return when {
        previous == null && read.viewerError != null -> ScopeState.Failed(read.viewerError)
        else -> ScopeState.Resolved((previous ?: BlocklistScope()).readWith(read))
    }
}

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
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
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

        /** Bumped on every arrival, so the scope below is re-read rather than fixed for the view model's life. */
        private val scopeRefresh = MutableStateFlow(0)

        /**
         * Resolved before anything downstream runs: the list's path is the profile's, so a page fetched
         * against a guessed one would be a request to the wrong place. Re-read on every arrival, since a
         * permission or a server upgrade is otherwise invisible while this view model lives. A first read
         * that cannot identify the viewer is [ScopeState.Failed], and a retry shows [ScopeState.Resolving]
         * again; once resolved, a read that fails keeps the last answer for that part.
         *
         * `flowOn(dispatcher)` for the same reason every `launch` here takes one (#177/#370): without
         * it, this flow's own suspend calls resume on `viewModelScope`'s `Dispatchers.Main.immediate`,
         * which can outlive a cleared scope same as a plain `launch` would.
         */
        private val scopeState: StateFlow<ScopeState> =
            scopeRefresh
                .flatMapLatest {
                    flow {
                        val previous = scopeState.value
                        if (previous is ScopeState.Failed) emit(ScopeState.Resolving)
                        emit(previous.readWith(readScope()))
                    }
                }.distinctUntilChanged()
                .flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.Lazily, ScopeState.Resolving)

        /** The resolved scope alone, which is all the lists and the counts wait on; `distinctUntilChanged` means re-entry restarts them only when it changed. */
        private val scope: Flow<BlocklistScope> =
            scopeState.filterIsInstance<ScopeState.Resolved>().map { it.scope }.distinctUntilChanged()

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
                combine(counts, scopeState) { counts, scope -> counts to scope },
                acting,
                listVersionState,
            ) { filter, search, (counts, scopeState), acting, listVersion ->
                when (scopeState) {
                    ScopeState.Resolving -> BlocklistUiState.Loading
                    is ScopeState.Failed -> BlocklistUiState.Error(scopeState.error)
                    is ScopeState.Resolved ->
                        BlocklistUiState.Ready(
                            filter = filter,
                            search = search,
                            counts = counts,
                            listVersion = listVersion,
                            hasFilters = scopeState.scope.hasFilters,
                            canManage = scopeState.scope.canManage,
                            canBlockCollections = scopeState.scope.canBlockCollections,
                            actingTmdbIds = acting,
                        )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, BlocklistUiState.Loading)

        fun setFilter(filter: BlocklistFilter) {
            selectedFilter.value = filter
        }

        fun setSearch(query: String) {
            search.value = query
        }

        /** Re-reads the signed-in user after [BlocklistUiState.Error]. */
        fun retry() {
            scopeRefresh.update { it + 1 }
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
                scopeRefresh.update { it + 1 }
                countsRefresh.update { it + 1 }
                listVersionState.update { it + 1 }
            }
        }

        fun remove(item: BlocklistItem) {
            if (item.tmdbId in acting.value) return
            acting.update { it + item.tmdbId }
            crashBreadcrumbs.key("tmdb_id", item.tmdbId.toString())
            crashBreadcrumbs.log("removing from blocklist")
            viewModelScope.launch(dispatcher) {
                runCatching {
                    connection.api().removeFromBlocklist(
                        connection.profile().blocklistPath,
                        item.tmdbId,
                        connection.profile().unblockMediaType(item.mediaType.seerrMediaType()),
                    )
                }.onSuccess {
                    countsRefresh.update { it + 1 }
                    listVersionState.update { it + 1 }
                    analytics.event(AnalyticsEvents.BLOCKLIST_CHANGED, mapOf(AnalyticsEvents.PARAM_ACTION to "removed"))
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
            crashBreadcrumbs.key("collection_id", collectionId.toString())
            crashBreadcrumbs.log(if (blocked) "blocking collection" else "unblocking collection")
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
                    val action = if (blocked) "collection_blocked" else "collection_unblocked"
                    analytics.event(AnalyticsEvents.BLOCKLIST_CHANGED, mapOf(AnalyticsEvents.PARAM_ACTION to action))
                    eventFlow.emit(BlocklistEvent.CollectionChanged(blocked))
                }.onFailure { eventFlow.emit(BlocklistEvent.Failed(it.toSeerrError())) }
            }
        }

        private suspend fun readScope(): ScopeRead {
            val viewer = runCatching { connection.refreshAuthenticatedUser() }
            return ScopeRead(
                profile = runCatching { connection.refreshProfile() }.getOrNull(),
                viewer = viewer.getOrNull(),
                viewerError = viewer.exceptionOrNull()?.toSeerrError(),
            )
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
