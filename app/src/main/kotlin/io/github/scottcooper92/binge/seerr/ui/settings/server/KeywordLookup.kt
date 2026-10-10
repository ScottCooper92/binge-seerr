package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.attempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Searches TMDB's keywords as the user types and names the ids a page holds. The blocklisted tags page and the override
 * rule's keyword picker both use it; each keeps the answer in its own state, through [edit], and nothing here saves.
 */
internal class KeywordLookup(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val api: suspend () -> SeerrApi,
    private val current: () -> KeywordSearch?,
    private val edit: ((KeywordSearch) -> KeywordSearch) -> Unit,
) {
    private var search: Job? = null

    /** Searches for [query]; a newer query cancels the one before, and blank clears the results. */
    fun search(query: String) {
        search?.cancel()
        if (query.isBlank()) {
            edit { it.copy(results = null, searching = false, failed = false) }
            return
        }
        search =
            scope.launch(dispatcher) {
                delay(KEYWORD_SEARCH_DEBOUNCE_MILLIS)
                edit { it.copy(searching = true, failed = false) }
                val found =
                    attempt { api().searchKeywords(query.trim()).results }
                        .getOrNull()
                        ?.mapNotNull { dto -> dto.name?.let { Keyword(dto.id, it) } }
                edit {
                    it.copy(
                        // A keyword added from the results is named without another read.
                        names = it.names + found.orEmpty().associate { k -> k.id to k.name },
                        results = found ?: it.results,
                        searching = false,
                        failed = found == null,
                    )
                }
            }
    }

    /** Names the [ids] not yet named, once each: the server keeps keywords as TMDB ids. */
    fun name(ids: List<Int>) {
        val missing = ids.filter { it !in current()?.names.orEmpty() }
        if (missing.isEmpty()) return
        scope.launch(dispatcher) {
            val api = api()
            val named = missing.mapNotNull { id -> attempt { api.keyword(id) }.getOrNull()?.name?.let { id to it } }.toMap()
            edit { it.copy(names = it.names + named) }
        }
    }
}
