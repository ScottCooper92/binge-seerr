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

    /** Production companies, a list of their own: no title is one. */
    Studios(null),
    ;

    fun keeps(result: TitleResult): Boolean = this != Studios && (kind == null || result.kind == kind)
}

/**
 * How many results each type chip shows. A type counts what has loaded so far, so it grows as pages arrive. [SearchType.All]
* counts every row loaded, collections included, so it always matches the list under it. The studios are a list of their own.
 */
internal fun List<TitleResult>.typeCounts(studios: Int = 0): Map<SearchType, Int> =
    SearchType.entries.associateWith { type -> if (type == SearchType.Studios) studios else count(type::keeps) }
