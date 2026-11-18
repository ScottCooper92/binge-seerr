package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.ui.openTitle
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType

/**
 * A hub in the tab bar. Each tab keeps its own view model, so Movies and TV load and refresh apart. A card opens the title in
 * Binge where it is installed and on the server where it is not, until the app has a title page of its own.
 */
@Composable
internal fun DiscoverHubEntry(tab: HubTab) {
    val model = hiltViewModel<HubViewModel, HubViewModel.Factory>(key = tab.name, creationCallback = { it.create(tab) })
    val state by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val promoted = LocalFlags.current.bingePromotion
    DiscoverHubScreen(
        tab = tab,
        state = state,
        onRefresh = model::refresh,
        onOpenTitle = { card ->
            val type = if (tab == HubTab.Movies) RequestMediaType.Movie else RequestMediaType.Tv
            context.openTitle(promoted, type, card.tmdbId, card.webUrl)
        },
    )
}
