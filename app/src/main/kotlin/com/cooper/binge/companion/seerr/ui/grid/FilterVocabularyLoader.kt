package com.cooper.binge.companion.seerr.ui.grid

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.core.search.QUERY_DEBOUNCE_MS
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.FilterLookups
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
internal class FilterVocabularyLoader(
    private val scope: CoroutineScope,
    private val kind: TitleKind,
    private val grid: DiscoverGridRepository,
    private val lookups: FilterLookups,
) {
    private val state = MutableStateFlow(FilterVocabulary())
    private val keywordQuery = MutableStateFlow("")
    private val regionsRequested = mutableSetOf<String>()

    val vocabulary: StateFlow<FilterVocabulary> = state

    /** Loads the lists, and names the keywords the grid was opened with. */
    fun load(namedKeywords: Set<Int>) {
        scope.launch {
            val capabilities = attempt { lookups.capabilities() }.getOrDefault(state.value.capabilities)
            val genres = attempt { grid.genres(kind) }.getOrDefault(emptyList())
            val languages = attempt { grid.languages() }.getOrDefault(emptyList())
            state.update { it.copy(genres = genres, languages = languages, capabilities = capabilities) }
            if (capabilities.watch) loadWatch()
            if (capabilities.certification) loadCertifications()
            if (namedKeywords.isNotEmpty()) nameKeywords(namedKeywords)
        }
        scope.launch {
            keywordQuery.debounce(QUERY_DEBOUNCE_MS).collectLatest { runKeywordSearch(it.trim()) }
        }
    }

    private suspend fun loadWatch() {
        val regions = attempt { lookups.regions() }.getOrDefault(emptyList())
        val default = attempt { lookups.defaultRegion() }.getOrNull()?.takeIf { code -> regions.any { it.id == code } }
        state.update { it.copy(regions = regions, defaultRegion = default) }
        default?.let { providersFor(it) }
    }

    private suspend fun loadCertifications() {
        val ratings = attempt { lookups.certifications(kind) }.getOrDefault(emptyMap())
        state.update { it.copy(certifications = ratings) }
    }

    /** Loads the streaming providers of [region], once; the sheet asks as the user picks a region. */
    fun needProviders(region: String) {
        if (regionsRequested.contains(region)) return
        scope.launch { providersFor(region) }
    }

    private suspend fun providersFor(region: String) {
        if (!regionsRequested.add(region)) return
        state.update { it.copy(providersFailed = it.providersFailed - region) }
        val providers = attempt { lookups.providers(kind, region) }
        providers.onFailure {
            regionsRequested.remove(region)
            state.update { it.copy(providersFailed = it.providersFailed + region) }
        }
        providers.onSuccess { found -> state.update { it.copy(providers = it.providers + (region to found)) } }
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
