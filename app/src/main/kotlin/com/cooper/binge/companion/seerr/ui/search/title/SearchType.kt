package com.cooper.binge.companion.seerr.ui.search.title

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult

/** The type chips above the results. The order is the order they are drawn in. */
enum class SearchType(
    private val kind: TitleKind?,
) {
    All(null),
    Movies(TitleKind.Movie),
    Series(TitleKind.Series),
    People(TitleKind.Person),
    ;

    fun keeps(result: TitleResult): Boolean = kind == null || result.kind == kind
}

/**
 * How many results each type chip shows. A type counts what has loaded so far, so it grows as pages arrive. [SearchType.All]
 * is the sum of the others, as Binge's is, so the chips always add up.
 */
internal fun List<TitleResult>.typeCounts(): Map<SearchType, Int> {
    val byType = SearchType.entries.filter { it != SearchType.All }.associateWith { type -> count(type::keeps) }
    return mapOf(SearchType.All to byType.values.sum()) + byType
}
