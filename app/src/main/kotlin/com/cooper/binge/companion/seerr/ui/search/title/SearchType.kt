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
 * is the sum of the title chips, as Binge's is: the studios are a list of their own and are not in it.
 */
internal fun List<TitleResult>.typeCounts(studios: Int = 0): Map<SearchType, Int> {
    val titles = listOf(SearchType.Movies, SearchType.Series, SearchType.People).associateWith { type -> count(type::keeps) }
    return mapOf(SearchType.All to titles.values.sum()) + titles + (SearchType.Studios to studios)
}
