package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.attempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Searches TMDB's companies as the user types and names the studio a slider holds, the way [KeywordLookup] does keywords.
 * The answer is kept in the page's own state, through [edit]; nothing here saves.
 */
internal class CompanyLookup(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val api: suspend () -> SeerrApi,
    private val current: () -> CompanySearch,
    private val edit: ((CompanySearch) -> CompanySearch) -> Unit,
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
                    attempt { api().searchCompanies(query.trim()).results }
                        .getOrNull()
                        ?.mapNotNull { dto -> dto.name?.takeIf { it.isNotBlank() }?.let { Company(dto.id, it) } }
                edit {
                    it.copy(
                        // A studio picked from the results is named without another read.
                        names = it.names + found.orEmpty().associate { c -> c.id to c.name },
                        results = found ?: it.results,
                        searching = false,
                        failed = found == null,
                    )
                }
            }
    }

    /** Names the studio [id] unless it is named already; a failed read leaves the id as it is. */
    fun name(id: Int) {
        if (id in current().names) return
        scope.launch(dispatcher) {
            val name = attempt { api().studio(id) }.getOrNull()?.name?.takeIf { it.isNotBlank() }
            if (name != null) edit { it.copy(names = it.names + (id to name)) }
        }
    }
}
