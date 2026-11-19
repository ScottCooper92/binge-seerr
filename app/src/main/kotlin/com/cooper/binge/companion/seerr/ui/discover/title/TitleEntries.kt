package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.flags.bingePromotion
import com.cooper.binge.companion.seerr.ui.CollectionRoute
import com.cooper.binge.companion.seerr.ui.DetailPane
import com.cooper.binge.companion.seerr.ui.DetailPaneContent
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.NavMotionSpecs
import com.cooper.binge.companion.seerr.ui.PersonDetailRoute
import com.cooper.binge.companion.seerr.ui.SeriesEpisodesRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.TitleCreditsRoute
import com.cooper.binge.companion.seerr.ui.TitleDetailRoute
import com.cooper.binge.companion.seerr.ui.UserDetailRoute
import com.cooper.binge.companion.seerr.ui.discover.collection.CollectionPageEntry
import com.cooper.binge.companion.seerr.ui.discover.person.PersonEntry
import com.cooper.binge.companion.seerr.ui.discover.title.credits.TitleCreditsEntry
import com.cooper.binge.companion.seerr.ui.discover.title.season.SeriesEpisodesEntry
import com.cooper.binge.companion.seerr.ui.openTitle
import com.cooper.binge.companion.seerr.ui.popIfTop
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.toRequestMediaType

/** The title pages: a film's or a series' page, every season's episodes of it, a collection, the credits, and a person's page. Each leaves through [popIfTop] with its own key. */
internal fun EntryProviderScope<NavKey>.titleEntries(
    backStack: NavBackStack<NavKey>,
    motion: NavMotionSpecs,
) {
    entry<TitleDetailRoute>(metadata = DetailPane + motion.detail) { route ->
        DetailPaneContent {
            TitleDetailEntry(
                route.kind,
                route.tmdbId,
                onBack = { backStack.popIfTop(route) },
                onOpenCredits = { backStack.add(TitleCreditsRoute(route.kind, route.tmdbId)) },
                onOpenSeason = { number, seriesName -> backStack.add(SeriesEpisodesRoute(route.tmdbId, seriesName, number)) },
                onOpenCompany = { company ->
                    backStack.add(DiscoverGridRoute(route.kind, GridPreset.Company(company.id), company.name))
                },
                onOpenKeyword = { keyword ->
                    backStack.add(DiscoverGridRoute(route.kind, GridPreset.Keyword(keyword.id), keyword.name))
                },
                onOpenUser = { id -> backStack.add(UserDetailRoute(id)) },
                onOpenServices = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.Services)) },
            )
        }
    }
    entry<CollectionRoute>(metadata = DetailPane + motion.detail) { route ->
        DetailPaneContent { CollectionPageEntry(route.id, onBack = { backStack.popIfTop(route) }) }
    }
    entry<PersonDetailRoute>(metadata = DetailPane + motion.detail) { route ->
        DetailPaneContent { PersonEntry(route.personId, onBack = { backStack.popIfTop(route) }) }
    }
    entry<SeriesEpisodesRoute>(metadata = DetailPane + motion.detail) { route ->
        DetailPaneContent {
            SeriesEpisodesEntry(route.tmdbId, route.seriesName, route.seasonNumber, onBack = { backStack.popIfTop(route) })
        }
    }
    entry<TitleCreditsRoute>(metadata = DetailPane + motion.detail) { route ->
        DetailPaneContent { TitleCreditsEntry(route.kind, route.tmdbId, onBack = { backStack.popIfTop(route) }) }
    }
}

/** The title hand-off: a film's, a series', a person's or a collection's page with the discovery flag on, and Binge or the server's own page as before otherwise. */
@Composable
internal fun rememberOpenTitle(backStack: NavBackStack<NavKey>): (TitleKind, Int, String) -> Unit {
    val discovery = LocalDiscoveryEnabled.current
    val promoted = LocalFlags.current.bingePromotion
    val context = LocalContext.current
    return remember(backStack, discovery, promoted, context) {
        { kind, id, webUrl ->
            if (discovery && kind == TitleKind.Collection) {
                backStack.add(CollectionRoute(id))
            } else if (discovery && kind == TitleKind.Person) {
                backStack.add(PersonDetailRoute(id))
            } else if (discovery && (kind == TitleKind.Movie || kind == TitleKind.Series)) {
                backStack.add(TitleDetailRoute(kind, id))
            } else {
                context.openTitle(promoted, kind.toRequestMediaType(), id, webUrl)
            }
        }
    }
}
