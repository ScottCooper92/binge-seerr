package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** What one tab has open over its board: a see-all grid by its key, and a detail page by id above it. */
@Stable
internal class TvTabPages(
    detailId: Int? = null,
    seeAll: String? = null,
) {
    /** A detail page, over the board or over the see-all grid it was opened from. */
    var detailId by mutableStateOf(detailId)

    /** A see-all grid, by its key. It stays up under a detail page opened from one of its cards. */
    var seeAll by mutableStateOf(seeAll)

    val anyOpen: Boolean get() = detailId != null || seeAll != null

    fun clear() {
        detailId = null
        seeAll = null
    }
}

/**
 * The pages open over each tab's root, one [TvTabPages] per tab. Each tab keeps its own, so switching away hides them and
 * switching back shows them again; choosing the current tab again clears them, as the phone's reselect does. Every tab
 * gets its pages here, so a tab added to [TvDestination] needs nothing in this class.
 */
@Stable
internal class TvOpenPages(
    initial: Map<TvDestination, TvTabPages> = emptyMap(),
) {
    private val tabs = Tabs.associateWith { initial[it] ?: TvTabPages() }

    /** [tab]'s pages; a sheet has none to ask for. */
    operator fun get(tab: TvDestination): TvTabPages = tabs.getValue(tab)

    fun anyOpen(tab: TvDestination): Boolean = tabs[tab]?.anyOpen == true

    fun clear(tab: TvDestination) {
        tabs[tab]?.clear()
    }

    /**
     * Keeps the shell on an offered tab, as the phone's tabbed back stack reconciles: a tab that stops being offered loses
     * its pages, so they do not come back with it, and if it was [tab] the start tab, the first of [offered], takes over.
     */
    fun reconcile(
        tab: TvDestination,
        offered: List<TvDestination>,
    ): TvDestination {
        tabs.keys.filterNot { it in offered }.forEach(::clear)
        return if (tab in offered) tab else offered.first()
    }

    companion object {
        /** The destinations that are tabs, the ones with pages of their own. */
        private val Tabs = TvDestination.entries.filterNot { it.isSheet }

        val Saver =
            listSaver<TvOpenPages, Any?>(
                save = { pages -> Tabs.flatMap { listOf(pages[it].detailId, pages[it].seeAll) } },
                restore = { saved ->
                    TvOpenPages(
                        Tabs
                            .mapIndexed { index, tab -> tab to TvTabPages(saved[index * 2] as Int?, saved[index * 2 + 1] as String?) }
                            .toMap(),
                    )
                },
            )
    }
}

@Composable
internal fun rememberTvOpenPages(): TvOpenPages = rememberSaveable(saver = TvOpenPages.Saver) { TvOpenPages() }
