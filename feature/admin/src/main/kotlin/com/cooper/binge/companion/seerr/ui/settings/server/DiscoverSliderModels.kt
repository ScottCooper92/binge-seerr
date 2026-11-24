package com.cooper.binge.companion.seerr.ui.settings.server

import com.cooper.binge.companion.seerr.ui.Choice

/** One TMDB company, a studio a slider can hold. */
data class Company(
    val id: Int,
    val name: String,
)

/** The studio search: [names] for the ids a slider holds, and [results] for the last search, null before one. */
data class CompanySearch(
    val names: Map<Int, String> = emptyMap(),
    val results: List<Company>? = null,
    val searching: Boolean = false,
    val failed: Boolean = false,
)

/** The streaming providers TMDB lists in the slider's region, for its kind: being read, read, or failed. */
sealed interface ProviderChoices {
    /** No region yet, so nothing to ask for. */
    data object Idle : ProviderChoices

    data object Loading : ProviderChoices

    data class Ready(
        val providers: List<Choice>,
    ) : ProviderChoices

    data object Failed : ProviderChoices
}

/**
 * What the slider editor holds beside the form: the keyword and studio searches and names, the network names read for
 * typed ids, the genres and the streaming providers of the kind, and the streaming regions.
 */
data class SliderExtras(
    val keywords: KeywordSearch = KeywordSearch(),
    val genres: GenreChoices = GenreChoices.Loading,
    val studios: CompanySearch = CompanySearch(),
    val networkNames: Map<Int, String> = emptyMap(),
    val regions: ListChoices? = null,
    val providers: ProviderChoices = ProviderChoices.Idle,
)
