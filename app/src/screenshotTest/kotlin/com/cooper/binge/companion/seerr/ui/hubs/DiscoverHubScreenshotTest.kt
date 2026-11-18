package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13Previews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError

/**
 * The Movies and TV hubs: rows of posters with the state each card carries, a row still loading below the ones that landed, and
 * the loading, empty and error pages. The Spanish frame carries the longest row titles and the large-text frames the headers
 * at 1.3x, 1.5x and 2x.
 */
class DiscoverHubScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun ready() = Frame(readyState())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readyWithPendingRow() = Frame(readyState(pending = 1))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun refreshing() = Frame(readyState(refreshing = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun series() = Frame(readyState(), tab = HubTab.Series)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun withHero() = Frame(readyState(hero = HEROES))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun withHeroLongTitle() = Frame(readyState(hero = listOf(LONG_HERO)))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun withHeroSpanish() = Frame(readyState(hero = HEROES))

    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun withHeroLargeText13() = Frame(readyState(hero = listOf(LONG_HERO)))

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun withHeroLargeText() = Frame(readyState(hero = listOf(LONG_HERO)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(DiscoverHubUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun empty() = Frame(DiscoverHubUiState.Empty)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun error() = Frame(DiscoverHubUiState.Error(SeerrError.Unreachable))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = Frame(readyState())

    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun largeText13() = Frame(readyState())

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun largeText() = Frame(readyState())
}

@Composable
private fun Frame(
    state: DiscoverHubUiState,
    tab: HubTab = HubTab.Movies,
) = DiscoverHubScreen(tab = tab, state = state, onRefresh = {}, onOpenTitle = {})

private fun card(
    id: Int,
    name: String,
    availability: TitleAvailability = TitleAvailability.None,
) = HubCard(TitleKind.Movie, id, name, "2020", null, availability, "https://server.example/movie/$id")

private val SAMPLE_CARDS =
    listOf(
        card(1, "The Long Way Home", TitleAvailability.Available),
        card(2, "Northern Lights", TitleAvailability.Pending),
        card(3, "A Quiet Harbour", TitleAvailability.Processing),
        card(4, "Paper Moons"),
        card(5, "Seven Rivers", TitleAvailability.PartiallyAvailable),
        card(6, "Blocked Title", TitleAvailability.Blocklisted),
    )

private fun readyState(
    pending: Int = 0,
    refreshing: Boolean = false,
    hero: List<HubHero> = emptyList(),
) = DiscoverHubUiState.Ready(
    rows =
        listOf(
            HubRowUi(HubRow.Trending(1, null, HubTab.Movies), SAMPLE_CARDS),
            HubRowUi(HubRow.RecentlyAdded(2, null, HubTab.Movies), SAMPLE_CARDS.reversed()),
            HubRowUi(HubRow.Popular(3, "Staff picks", HubTab.Movies), SAMPLE_CARDS.take(4)),
        ),
    pending = pending,
    refreshing = refreshing,
    hero = hero,
)

private fun hero(
    id: Int,
    name: String,
    availability: TitleAvailability,
) = HubHero(
    TitleKind.Movie,
    id,
    name,
    "https://image.tmdb.org/t/p/w1280/none$id.jpg",
    "2021",
    7.8f,
    availability,
    "https://server.example/movie/$id",
)

private val HEROES =
    listOf(
        hero(1, "The Long Way Home", TitleAvailability.Available),
        hero(2, "Northern Lights", TitleAvailability.Pending),
        hero(3, "A Quiet Harbour", TitleAvailability.None),
    )

private val LONG_HERO =
    hero(
        4,
        "The Extraordinarily Long Title of a Film That Keeps Going: Part Two, the Return of the Seven Rivers",
        TitleAvailability.PartiallyAvailable,
    )
