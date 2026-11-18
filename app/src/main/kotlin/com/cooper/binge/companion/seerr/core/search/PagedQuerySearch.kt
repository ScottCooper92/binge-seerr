package com.cooper.binge.companion.seerr.core.search

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.core.grid.PagedList
import com.cooper.binge.companion.seerr.core.grid.PagedListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Debounce before a typed query is searched. */
const val QUERY_DEBOUNCE_MS = 300L

/** The fewest characters a query must have before it is searched. */
const val MIN_QUERY_LENGTH = 2

/**
 * Search over a paged source: the query text, a debounce, the history, and a [PagedList] for the paging. A query that changes
 * while one is loading drops the old one, pages included. [fetch] is the whole of what the source contributes, and [keyOf]
 * drops a result already listed, as a [PagedList] does.
 *
 * It knows nothing of what it searches, so it holds no strings and no types of the source.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PagedQuerySearch<T>(
    scope: CoroutineScope,
    private val history: QueryHistory,
    fetch: suspend (query: String, page: Int) -> SearchPage<T>,
    private val debounceMs: Long = QUERY_DEBOUNCE_MS,
    private val minQueryLength: Int = MIN_QUERY_LENGTH,
    started: SharingStarted = SharingStarted.Lazily,
    keyOf: ((T) -> Any)? = null,
) {
    /** A query; [immediate] skips the debounce, for a submit. */
    private data class Input(
        val query: String,
        val immediate: Boolean,
    )

    private val input = MutableStateFlow(Input(query = "", immediate = false))
    private val text = MutableStateFlow("")
    private var remembered: String? = null
    private val noResults = SearchPage<T>(emptyList(), page = 1, totalPages = 1)

    /** What the field shows: the text as typed, not the debounced query. */
    val query: StateFlow<String> = text

    private val settled =
        input
            .debounce { if (it.immediate) 0L else debounceMs }
            .map { it.query }
            .distinctUntilChanged()
            // Shared eagerly so the list and the state read one debounced stream, whatever `started` is.
            .stateIn(scope, SharingStarted.Eagerly, "")

    /** Every settled query reaches the list, a short one as an empty page, so the next real query is a change and searches again. */
    private val list =
        PagedList(
            scope = scope,
            query = settled,
            fetch = { q, page -> if (q.length < minQueryLength) noResults else fetch(q, page).also { remember(q, it) } },
            started = started,
            keyOf = keyOf,
        )

    val state: StateFlow<QuerySearchState<T>> =
        combine(settled, list.answered, history.recents) { q, answered, recents ->
            when {
                q.length < minQueryLength -> QuerySearchState.Idle(recents)
                answered?.query != q -> QuerySearchState.Loading(q)
                else -> answered.state.toSearchState(q)
            }
        }.stateIn(scope, started, QuerySearchState.Idle(emptyList()))

    fun setQuery(value: String) {
        text.value = value
        input.update { it.copy(query = value.trim(), immediate = false) }
    }

    fun clear() = setQuery("")

    /** Search the query now, without waiting out the debounce. */
    fun submit() = input.update { it.copy(immediate = true) }

    /** Try the current query again after a failure. */
    fun retry() = list.retry()

    /** Ask for the next page. One at a time, and none if the results have run out. */
    fun loadMore() = list.loadMore()

    suspend fun forgetRecents() {
        history.clear()
        remembered = null
    }

    /** Remembers a query once its results are non-empty, and only once until another query or the history being cleared. */
    private suspend fun remember(
        query: String,
        found: SearchPage<T>,
    ) {
        if (found.items.isEmpty() || remembered == query) return
        remembered = query
        attempt { history.remember(query) }
    }

    private fun PagedListState<T>.toSearchState(query: String): QuerySearchState<T> =
        when (this) {
            PagedListState.Loading -> QuerySearchState.Loading(query)
            PagedListState.Empty -> QuerySearchState.NoResults(query)
            is PagedListState.Failed -> QuerySearchState.Failed(query, cause)
            is PagedListState.Ready -> QuerySearchState.Results(query, items, hasMore, appending, appendFailed)
        }
}
