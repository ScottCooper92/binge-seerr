package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SheetFrame
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import kotlinx.coroutines.flow.emptyFlow

/**
 * Settings › Discover sliders: the reorderable list of the server's Discover rows, and the form for a
 * custom one. The list takes the device matrix once; the form takes its layout once, then each arm on the
 * phone cell alone.
 */
class DiscoverSlidersScreenshotTest {
    /** Built-in rows named by their kind, custom ones by their title with the kind and query beneath, one of them off. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun listLayout() = SlidersFrame(settledSliders(sliders()))

    /** A slider of a kind this app does not know is named by its number, and cannot be opened. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unknownKind() =
        SlidersFrame(
            settledSliders(
                sliders() + DiscoverSlider(id = 99, typeCode = 77, title = null, builtIn = false, enabled = true, data = null),
            ),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unsavedOrder() = SlidersFrame(ExtrasEditorUiState.Ready(draft = sliders().reversed(), saved = sliders(), extras = sliderNames))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun saving() = SlidersFrame(ExtrasEditorUiState.Ready(draft = sliders(), saved = sliders(), extras = sliderNames, saving = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = SlidersFrame(ExtrasEditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = SlidersFrame(ExtrasEditorUiState.Error(SeerrError.Unreachable))
}

/** The slider list's overflow sheet, as the stateless body a modal window will not capture: reset, in the error colour. */
class DiscoverSlidersSheetScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun overflow() =
        SheetFrame {
            SlidersOverflowContent(onReset = {})
        }
}

class DiscoverSliderFormScreenshotTest {
    /** A new slider: the first custom kind, with nothing picked, so Save stays off. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun newLayout() = SliderFormFrame(readyForm(SliderForm()))

    /** A saved keyword slider: its keywords named on the row, and Delete. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun existing() = SliderFormFrame(readyForm(savedSlider(), keywordNames))

    /** A genre slider names its genre, from the list of its kind. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun genreKind() = SliderFormFrame(readyForm(savedSlider().copy(type = SliderType.MovieGenre, data = "878"), genres = genreList))

    /** A server that cannot send the genres gets the typed id back, so the slider stays in reach. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun genresUnavailable() =
        SliderFormFrame(readyForm(savedSlider().copy(type = SliderType.MovieGenre, data = "878"), genres = GenreChoices.Failed))

    /** A studio slider names its studio, from TMDB's company search. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun studioKind() =
        SliderFormFrame(
            readyForm(
                savedSlider().copy(type = SliderType.Studio, title = "Marvel", data = "420"),
                SliderExtras(studios = CompanySearch(names = mapOf(420 to "Marvel Studios"))),
            ),
        )

    /** A network is typed as its id, and named once the server has said which it is. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun networkKind() =
        SliderFormFrame(
            readyForm(
                savedSlider().copy(type = SliderType.Network, title = "HBO", data = "49"),
                SliderExtras(networkNames = mapOf(49 to "HBO")),
            ),
        )

    /** A streaming slider: the region, then the providers TMDB lists there, some picked. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun streamingKind() =
        SliderFormFrame(
            readyForm(
                savedSlider().copy(type = SliderType.MovieStreamingServices, title = "Streaming", data = "GB,8|337"),
                SliderExtras(
                    providers = ProviderChoices.Ready(listOf(Choice(8, "Netflix"), Choice(337, "Disney Plus"), Choice(9, "Prime Video"))),
                ),
            ),
        )

    /** A new streaming slider has no region yet, so its providers wait for one. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun streamingNoRegion() = SliderFormFrame(readyForm(SliderForm(type = SliderType.TvStreamingServices, title = "Streaming")))

    /** A search slider, whose data hint is about a query rather than ids. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun searchKind() = SliderFormFrame(readyForm(savedSlider().copy(type = SliderType.Search, data = "dune")))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unsavedEdit() =
        SliderFormFrame(
            ExtrasEditorUiState.Ready(draft = savedSlider().copy(title = "Kaiju"), saved = savedSlider(), extras = keywordNames),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = SliderFormFrame(ExtrasEditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = SliderFormFrame(ExtrasEditorUiState.Error(SeerrError.Server))
}

private val keywordNames = SliderExtras(keywords = KeywordSearch(names = mapOf(210024 to "kaiju", 4344 to "monster")))

private val genreList =
    GenreChoices.Ready(listOf(Choice(28, "Action"), Choice(878, "Science Fiction"), Choice(12, "Adventure")))

private fun readyForm(
    form: SliderForm,
    extras: SliderExtras = SliderExtras(),
    genres: GenreChoices = extras.genres,
) = ExtrasEditorUiState.Ready(draft = form, saved = form, extras = extras.copy(genres = genres))

private fun settledSliders(list: List<DiscoverSlider>) = ExtrasEditorUiState.Ready(draft = list, saved = list, extras = sliderNames)

/** What the list has read to name its custom sliders: the keywords, the genre, the studio, the network and the providers. */
private val sliderNames =
    SliderNames(
        keywords = mapOf(210024 to "kaiju", 4344 to "monster"),
        studios = mapOf(420 to "Marvel Studios"),
        networks = mapOf(49 to "HBO"),
        genres = mapOf("movie" to mapOf(878 to "Science Fiction")),
        providers = mapOf("movies:GB" to mapOf(8 to "Netflix", 337 to "Disney Plus")),
    )

private fun sliders() =
    listOf(
        DiscoverSlider(id = 1, typeCode = 1, title = null, builtIn = true, enabled = true, data = null),
        DiscoverSlider(id = 2, typeCode = 2, title = null, builtIn = true, enabled = true, data = null),
        DiscoverSlider(id = 3, typeCode = 4, title = null, builtIn = true, enabled = false, data = null),
        DiscoverSlider(id = 4, typeCode = 13, title = "Kaiju films", builtIn = false, enabled = true, data = "210024,4344"),
        DiscoverSlider(id = 5, typeCode = 19, title = "Dune", builtIn = false, enabled = false, data = "dune"),
        DiscoverSlider(id = 6, typeCode = 5, title = null, builtIn = true, enabled = true, data = null),
        DiscoverSlider(id = 7, typeCode = 15, title = "Sci-fi", builtIn = false, enabled = true, data = "878"),
        DiscoverSlider(id = 8, typeCode = 17, title = "Marvel", builtIn = false, enabled = true, data = "420"),
        DiscoverSlider(id = 9, typeCode = 18, title = "HBO", builtIn = false, enabled = true, data = "49"),
        DiscoverSlider(id = 10, typeCode = 20, title = "Streaming", builtIn = false, enabled = true, data = "GB,8|337"),
    )

private fun savedSlider() = SliderForm(id = 4, type = SliderType.MovieKeyword, title = "Kaiju films", data = "210024,4344")

private fun <T> noActions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

@Composable
private fun SlidersFrame(state: ExtrasEditorUiState<List<DiscoverSlider>, SliderNames>) =
    DiscoverSlidersScreen(
        state = state,
        events = emptyFlow(),
        actions = noActions(),
        sliderActions = SlidersActions(onMove = { _, _ -> }, onToggle = {}, onOpenSlider = {}, onReset = {}),
    )

@Composable
private fun SliderFormFrame(state: ExtrasEditorUiState<SliderForm, SliderExtras>) =
    DiscoverSliderScreen(
        state = state,
        events = emptyFlow(),
        actions = noActions(),
        sliderActions =
            SliderEditorActions(
                onSelectType = {},
                onToggleKeyword = {},
                onSelectGenre = {},
                onSearchKeywords = {},
                onLoadKeywordNames = {},
                onSearchStudios = {},
                onSelectStudio = {},
                onNameNetwork = {},
                onLoadRegions = {},
                onSelectRegion = {},
                onToggleProvider = {},
                onDelete = {},
            ),
    )
