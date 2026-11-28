package com.cooper.binge.companion.seerr.ui.grid

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.FilterLookups
import com.cooper.homelab.core.presentation.search.QUERY_DEBOUNCE_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads what the filter sheet's pickers offer, for a grid of [kind]: once for the lists, and on demand for the streaming
 * providers of a region and the keywords a user searches. It holds no Android type, so the view model that owns it is tested
 * on the JVM.
 */
@OptIn(FlowPreview::class)
class FilterVocabularyLoader(
    private val scope: CoroutineScope,
    private val kind: TitleKind,
    private val grid: DiscoverGridRepository,
    private val lookups: FilterLookups,
) {
    private val state = MutableStateFlow(FilterVocabulary())
    private val keywordQuery = MutableStateFlow("")
    private val regionsRequested = mutableSetOf<String>()
    private var opened: Set<Int> = emptySet()
    private var listsFailed = false

    val vocabulary: StateFlow<FilterVocabulary> = state

    /** Loads the lists, and names the keywords the grid was opened with. */
    fun load(namedKeywords: Set<Int>) {
        opened = namedKeywords
        scope.launch { loadLists() }
        scope.launch {
            keywordQuery.debounce(QUERY_DEBOUNCE_MS).collectLatest { runKeywordSearch(it.trim()) }
        }
    }

    /** Asks again for the lists that did not load, so the grid's Retry brings back the filter rows as well as the titles. */
    fun retryFailed() {
        if (!listsFailed) return
        scope.launch { loadLists() }
    }

    private suspend fun loadLists() {
        listsFailed = false
        val capabilities = attempt { lookups.capabilities() }.onFailure { listsFailed = true }.getOrDefault(state.value.capabilities)
        val genres = attempt { grid.genres(kind) }.onFailure { listsFailed = true }.getOrDefault(emptyList())
        val languages = attempt { grid.languages() }.onFailure { listsFailed = true }.getOrDefault(emptyList())
        state.update { it.copy(genres = genres, languages = languages, capabilities = capabilities) }
        if (capabilities.watch) loadWatch()
        if (capabilities.certification) loadCertifications()
        if (opened.isNotEmpty()) nameKeywords(opened)
    }

    private suspend fun loadWatch() {
        val regions = attempt { lookups.regions() }.onFailure { listsFailed = true }.getOrDefault(emptyList())
        val default = attempt { lookups.defaultRegion() }.getOrNull()?.takeIf { code -> regions.any { it.id == code } }
        state.update { it.copy(regions = regions, defaultRegion = default) }
        default?.let { providersFor(it) }
    }

    private suspend fun loadCertifications() {
        val ratings = attempt { lookups.certifications(kind) }.onFailure { listsFailed = true }.getOrDefault(emptyMap())
        state.update { it.copy(certifications = ratings) }
    }

    /** Loads the streaming providers of [region], once; the sheet asks as the user picks a region. */
    fun needProviders(region: String) {
        if (regionsRequested.contains(region)) return
        scope.launch { providersFor(region) }
    }

    private suspend fun providersFor(region: String) {
        if (!regionsRequested.add(region)) return
        val providers = attempt { lookups.providers(kind, region) }
        val found = providers.getOrNull()
        if (found == null) regionsRequested.remove(region)
        state.update {
            if (found == null) {
                it.copy(providersFailed = it.providersFailed + region)
            } else {
                it.copy(providers = it.providers + (region to found), providersFailed = it.providersFailed - region)
            }
        }
    }

    /** Searches the keywords for [query]; a newer one replaces it, and blank clears the results. */
    fun searchKeywords(query: String) {
        keywordQuery.value = query
        if (query.isBlank()) state.update { it.copy(keywordResults = null, keywordsFailed = false) }
    }

    private suspend fun runKeywordSearch(query: String) {
        if (query.isBlank()) return
        val found = attempt { lookups.searchKeywords(query) }.getOrNull()
        state.update {
            it.copy(
                keywordResults = found ?: it.keywordResults,
                keywordsFailed = found == null,
                keywordNames = it.keywordNames + found.orEmpty().associate { option -> option.id.toInt() to option.name },
            )
        }
    }

    /** Names the keywords with these [ids] that are not yet named. */
    fun nameKeywords(ids: Set<Int>) {
        val missing = ids - state.value.keywordNames.keys
        if (missing.isEmpty()) return
        scope.launch {
            val names = attempt { lookups.keywordNames(missing) }.getOrDefault(emptyMap())
            state.update { it.copy(keywordNames = it.keywordNames + names) }
        }
    }
}
