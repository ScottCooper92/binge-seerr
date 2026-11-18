package com.cooper.binge.companion.seerr.core.grid

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What a filterable list is asked for: the [filters] that narrow it and the [sort] that orders it. */
data class SortFilterQuery<F, S>(
    val filters: F,
    val sort: S,
)

/**
 * The sort and filters of a list, held apart from what they mean. [F] and [S] are the source's own types, so this knows no
 * parameter names. [query] is what a [PagedList] keys on, so a change to either starts the list again from its first page.
 */
class SortFilterState<F, S>(
    initialFilters: F,
    initialSort: S,
) {
    private val state = MutableStateFlow(SortFilterQuery(initialFilters, initialSort))

    val query: StateFlow<SortFilterQuery<F, S>> = state.asStateFlow()

    fun setSort(sort: S) = state.update { it.copy(sort = sort) }

    fun updateFilters(change: (F) -> F) = state.update { it.copy(filters = change(it.filters)) }

    fun set(
        filters: F,
        sort: S,
    ) = state.update { SortFilterQuery(filters, sort) }

    fun setFilters(filters: F) = state.update { it.copy(filters = filters) }
}
