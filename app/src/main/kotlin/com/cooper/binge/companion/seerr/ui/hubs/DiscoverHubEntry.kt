package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.HubTile
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle

/**
 * A hub in the tab bar. Each tab keeps its own view model, so Movies and TV load and refresh apart. A card opens the title's page, or with
 * the flag off the hand-off [LocalOpenTitle] falls back to.
 */
@Composable
internal fun DiscoverHubEntry(
    tab: HubTab,
    onOpenCategory: (HubRow, HubTile) -> Unit,
) {
    val model = hiltViewModel<HubViewModel, HubViewModel.Factory>(key = tab.name, creationCallback = { it.create(tab) })
    val state by model.uiState.collectAsStateWithLifecycle()
    val openTitle = LocalOpenTitle.current
    DiscoverHubScreen(
        tab = tab,
        state = state,
        onRefresh = model::refresh,
        onOpenTitle = { card -> openTitle(card.kind, card.tmdbId, card.webUrl) },
        onOpenCategory = onOpenCategory,
    )
}
