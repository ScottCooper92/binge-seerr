package com.cooper.binge.companion.seerr.core.search

import com.binge.companion.sdk.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Debounce before a typed query is searched. */
const val QUERY_DEBOUNCE_MS = 300L

/** The fewest characters a query must have before it is searched. */
const val MIN_QUERY_LENGTH = 2

/**
 * Search over a paged source: the query text, a debounce, one request in flight, further pages, and the history. A query that
 * changes while one is loading drops the old one, pages included. [fetch] is the whole of what the source contributes.
 *
 * It knows nothing of what it searches, so it holds no strings and no types of the source.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PagedQuerySearch<T>(
    scope: CoroutineScope,
    private val history: QueryHistory,
    private val fetch: suspend (query: String, page: Int) -> SearchPage<T>,
    private val debounceMs: Long = QUERY_DEBOUNCE_MS,
    private val minQueryLength: Int = MIN_QUERY_LENGTH,
    started: SharingStarted = SharingStarted.Lazily,
) {
    /** A query and the attempt it is made under; [immediate] skips the debounce, for a retry or a submit. */
    private data class Input(
        val query: String,
        val attempt: Int,
        val immediate: Boolean,
    )

    private val input = MutableStateFlow(Input(query = "", attempt = 0, immediate = false))
    private val moreRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val text = MutableStateFlow("")

    /** What the field shows: the text as typed, not the debounced query. */
    val query: StateFlow<String> = text

    val state: StateFlow<QuerySearchState<T>> =
        input
            .debounce { if (it.immediate) 0L else debounceMs }
            .distinctUntilChanged { old, new -> old.query == new.query && old.attempt == new.attempt }
            .flatMapLatest { searchFlow(it.query) }
            .stateIn(scope, started, QuerySearchState.Idle(emptyList()))

    fun setQuery(value: String) {
        text.value = value
        input.update { it.copy(query = value.trim(), immediate = false) }
    }

    fun clear() = setQuery("")

    /** Search the query now, without waiting out the debounce. */
    fun submit() = input.update { it.copy(immediate = true) }

    /** Try the current query again after a failure. */
    fun retry() = input.update { it.copy(attempt = it.attempt + 1, immediate = true) }

    /** Ask for the next page. One at a time, and none if the results have run out. */
    fun loadMore() {
        moreRequests.tryEmit(Unit)
    }

    suspend fun forgetRecents() = history.clear()

    private fun searchFlow(query: String): Flow<QuerySearchState<T>> =
        if (query.length < minQueryLength) {
            history.recents.map { QuerySearchState.Idle(it) }
        } else {
            flow {
                emit(QuerySearchState.Loading(query))
                val first = attempt { fetch(query, 1) }.getOrElse { return@flow emit(QuerySearchState.Failed(query)) }
                if (first.items.isEmpty()) return@flow emit(QuerySearchState.NoResults(query))
                attempt { history.remember(query) }
                var shown = QuerySearchState.Results(query, first.items, first.hasMore)
                emit(shown)
                var last = first
                moreRequests.collect {
                    if (!last.hasMore) return@collect
                    emit(shown.copy(appending = true, appendFailed = false))
                    attempt { fetch(query, last.page + 1) }
                        .onSuccess { next ->
                            last = next
                            shown = shown.copy(items = shown.items + next.items, hasMore = next.hasMore)
                            emit(shown)
                        }.onFailure { emit(shown.copy(appendFailed = true)) }
                }
            }
        }
}
