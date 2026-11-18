package com.cooper.binge.companion.seerr.core.grid

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.core.search.SearchPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What a paged list shows. */
sealed interface PagedListState<out T> {
    data object Loading : PagedListState<Nothing>

    /** [appending] is a further page on its way; [appendFailed] is the last attempt at one failing, to be tried again. */
    data class Ready<T>(
        val items: List<T>,
        val hasMore: Boolean,
        val appending: Boolean = false,
        val appendFailed: Boolean = false,
    ) : PagedListState<T>

    data object Empty : PagedListState<Nothing>

    /** [cause] is what the source threw, for the caller to word. */
    data class Failed(
        val cause: Throwable,
    ) : PagedListState<Nothing>
}

/**
 * A paged list over any query [Q]: the first page when the query changes, further pages on request, and a retry. A query that
 * changes while one is loading drops the old one, pages included. [fetch] is the whole of what the source contributes, and
 * [Q] is whatever it keys on: it knows nothing of what is listed, so it holds no strings and no types of the source. With a
 * [keyOf], an item whose key is already listed is dropped, and a page of nothing but repeats is read past like an empty one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagedList<Q, T>(
    scope: CoroutineScope,
    query: Flow<Q>,
    private val fetch: suspend (query: Q, page: Int) -> SearchPage<T>,
    started: SharingStarted = SharingStarted.Lazily,
    private val keyOf: ((T) -> Any)? = null,
) {
    private val attempt = MutableStateFlow(0)
    private val moreRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val state: StateFlow<PagedListState<T>> =
        combine(query.distinctUntilChanged(), attempt) { q, _ -> q }
            .flatMapLatest { listFlow(it) }
            .stateIn(scope, started, PagedListState.Loading)

    /** Try the current query again after a failure. */
    fun retry() = attempt.update { it + 1 }

    /** Ask for the next page. One at a time, and none if the results have run out. */
    fun loadMore() {
        moreRequests.tryEmit(Unit)
    }

    private fun listFlow(query: Q): Flow<PagedListState<T>> =
        flow {
            emit(PagedListState.Loading)
            val seen = mutableSetOf<Any>()
            val first = attempt { readPage(query, 1, seen) }.getOrElse { return@flow emit(PagedListState.Failed(it)) }
            if (first.items.isEmpty()) return@flow emit(PagedListState.Empty)
            var shown = PagedListState.Ready(first.items, first.hasMore)
            emit(shown)
            var last = first
            moreRequests.collect {
                if (!last.hasMore) return@collect
                emit(shown.copy(appending = true, appendFailed = false))
                attempt { readPage(query, last.page + 1, seen) }
                    .onSuccess { next ->
                        last = next
                        shown = shown.copy(items = shown.items + next.items, hasMore = next.hasMore)
                        emit(shown)
                    }.onFailure { emit(shown.copy(appendFailed = true)) }
            }
        }

    /** A page that adds nothing new while more remain is read past, so an empty page, or one of repeats, never ends the paging. */
    private suspend fun readPage(
        query: Q,
        from: Int,
        seen: MutableSet<Any>,
    ): SearchPage<T> {
        var number = from
        while (true) {
            val page = fetch(query, number)
            val added = if (keyOf == null) page.items else page.items.filter { seen.add(keyOf.invoke(it)) }
            if (added.isNotEmpty() || !page.hasMore) return page.copy(items = added)
            number++
        }
    }
}
