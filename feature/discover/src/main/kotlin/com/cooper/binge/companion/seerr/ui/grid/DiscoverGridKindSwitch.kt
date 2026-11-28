package com.cooper.binge.companion.seerr.ui.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.grid.carriedTo
import com.cooper.binge.companion.seerr.discover.grid.carriedToOtherKind
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute

/**
 * The route for the other kind. The filters arrive already stripped of the old kind's, at the switch in the sheet
 * (`DiscoverFilterDraft.withKind`), so a choice made after it is kept. The title stays only where it names something neither kind owns, a language, a keyword or a
 * streaming service: the titles of the others, "Popular movies" and the like, name the kind they were opened for.
 */
fun DiscoverGridRoute.carriedTo(
    kind: TitleKind,
    sort: DiscoverSort,
    filters: DiscoverFilters,
): DiscoverGridRoute {
    val carried = preset.carriedToOtherKind()
    val keepsTitle = carried == preset && (preset is GridPreset.Language || preset is GridPreset.Keyword || preset is GridPreset.Provider)
    return DiscoverGridRoute(kind, carried, title.takeIf { keepsTitle }, filters, sort.carriedTo(kind))
}
