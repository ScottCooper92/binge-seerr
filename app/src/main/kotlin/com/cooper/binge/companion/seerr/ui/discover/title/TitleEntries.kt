package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.ui.DetailPane
import com.cooper.binge.companion.seerr.ui.DetailPaneContent
import com.cooper.binge.companion.seerr.ui.SeasonEpisodesRoute
import com.cooper.binge.companion.seerr.ui.TitleCreditsRoute
import com.cooper.binge.companion.seerr.ui.TitleDetailRoute
import com.cooper.binge.companion.seerr.ui.discover.title.credits.TitleCreditsEntry
import com.cooper.binge.companion.seerr.ui.discover.title.season.SeasonEpisodesEntry
import com.cooper.binge.companion.seerr.ui.openTitle
import com.cooper.binge.companion.seerr.ui.popIfTop
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType

/** The title pages: a film's or a series' page, a season of it, and the credits. Each leaves through [popIfTop] with its own key. */
fun EntryProviderScope<NavKey>.titleEntries(backStack: NavBackStack<NavKey>) {
    entry<TitleDetailRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            TitleDetailEntry(
                route.kind,
                route.tmdbId,
                onBack = { backStack.popIfTop(route) },
                onOpenCredits = { backStack.add(TitleCreditsRoute(route.kind, route.tmdbId)) },
                onOpenSeason = { number, name -> backStack.add(SeasonEpisodesRoute(route.tmdbId, number, name)) },
            )
        }
    }
    entry<SeasonEpisodesRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            SeasonEpisodesEntry(route.tmdbId, route.seasonNumber, route.seasonName, onBack = { backStack.popIfTop(route) })
        }
    }
    entry<TitleCreditsRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { TitleCreditsEntry(route.kind, route.tmdbId, onBack = { backStack.popIfTop(route) }) }
    }
}

/** The title hand-off: a film's or a series' page with the discovery flag on, and Binge or the server's own page as before otherwise. */
@Composable
internal fun rememberOpenTitle(backStack: NavBackStack<NavKey>): (TitleKind, Int, String) -> Unit {
    val discovery = LocalDiscoveryEnabled.current
    val promoted = LocalFlags.current.bingePromotion
    val context = LocalContext.current
    return remember(backStack, discovery, promoted, context) {
        { kind, id, webUrl ->
            // A person and a collection open as they did until their own pages land.
            if (discovery && (kind == TitleKind.Movie || kind == TitleKind.Series)) {
                backStack.add(TitleDetailRoute(kind, id))
            } else {
                context.openTitle(promoted, if (kind == TitleKind.Series) RequestMediaType.Tv else RequestMediaType.Movie, id, webUrl)
            }
        }
    }
}
