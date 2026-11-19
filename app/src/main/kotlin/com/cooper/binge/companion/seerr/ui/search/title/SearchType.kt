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
 * counts every row loaded, collections included, so it always matches the list under it.
 */
internal fun List<TitleResult>.typeCounts(): Map<SearchType, Int> = SearchType.entries.associateWith { type -> count(type::keeps) }
