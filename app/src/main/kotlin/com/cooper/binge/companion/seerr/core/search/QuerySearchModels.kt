package com.cooper.binge.companion.seerr.core.search

import kotlinx.coroutines.flow.Flow

/** One page of results for a query; pages count from 1. */
data class SearchPage<out T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}

/** What a query search shows. [query] is the trimmed text the state answers. */
sealed interface QuerySearchState<out T> {
    /** No query yet, or one too short to search: the [recents], newest first, are what is offered. */
    data class Idle(
        val recents: List<String>,
    ) : QuerySearchState<Nothing>

    data class Loading(
        val query: String,
    ) : QuerySearchState<Nothing>

    /** [appending] is a further page on its way; [appendFailed] is the last attempt at one failing, to be tried again. */
    data class Results<T>(
        val query: String,
        val items: List<T>,
        val hasMore: Boolean,
        val appending: Boolean = false,
        val appendFailed: Boolean = false,
    ) : QuerySearchState<T>

    data class NoResults(
        val query: String,
    ) : QuerySearchState<Nothing>

    data class Failed(
        val query: String,
    ) : QuerySearchState<Nothing>
}

/** The queries a user has searched, kept on the device. */
interface QueryHistory {
    /** Newest first. */
    val recents: Flow<List<String>>

    suspend fun remember(query: String)

    suspend fun clear()
}
