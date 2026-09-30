package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
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
    fun unsavedOrder() = SlidersFrame(EditorUiState.Ready(draft = sliders().reversed(), saved = sliders()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun saving() = SlidersFrame(EditorUiState.Ready(draft = sliders(), saved = sliders(), saving = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = SlidersFrame(EditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = SlidersFrame(EditorUiState.Error(SeerrError.Unreachable))
}

class DiscoverSliderFormScreenshotTest {
    /** A new slider: the first custom kind, with nothing typed, so Save stays off. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun newLayout() = SliderFormFrame(EditorUiState.Ready(draft = SliderForm(), saved = SliderForm()))

    /** A saved slider: its kind's hint under the data field, and Delete. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun existing() = SliderFormFrame(settledSliders(savedSlider()))

    /** A search slider, whose data hint is about a query rather than ids. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun searchKind() = SliderFormFrame(settledSliders(savedSlider().copy(type = SliderType.Search, data = "dune")))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unsavedEdit() = SliderFormFrame(EditorUiState.Ready(draft = savedSlider().copy(title = "Kaiju"), saved = savedSlider()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = SliderFormFrame(EditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = SliderFormFrame(EditorUiState.Error(SeerrError.Server))
}

private fun <T> settledSliders(form: T) = EditorUiState.Ready(draft = form, saved = form)

private fun sliders() =
    listOf(
        DiscoverSlider(id = 1, typeCode = 1, title = null, builtIn = true, enabled = true, data = null),
        DiscoverSlider(id = 2, typeCode = 2, title = null, builtIn = true, enabled = true, data = null),
        DiscoverSlider(id = 3, typeCode = 4, title = null, builtIn = true, enabled = false, data = null),
        DiscoverSlider(id = 4, typeCode = 13, title = "Kaiju films", builtIn = false, enabled = true, data = "210024,4344"),
        DiscoverSlider(id = 5, typeCode = 19, title = "Dune", builtIn = false, enabled = false, data = "dune"),
        DiscoverSlider(id = 6, typeCode = 5, title = null, builtIn = true, enabled = true, data = null),
    )

private fun savedSlider() = SliderForm(id = 4, type = SliderType.MovieKeyword, title = "Kaiju films", data = "210024,4344")

private fun <T> noActions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

@Composable
private fun SlidersFrame(state: EditorUiState<List<DiscoverSlider>>) =
    DiscoverSlidersScreen(
        state = state,
        events = emptyFlow(),
        actions = noActions(),
        sliderActions = SlidersActions(onMove = { _, _ -> }, onToggle = {}, onOpenSlider = {}, onReset = {}),
    )

@Composable
private fun SliderFormFrame(state: EditorUiState<SliderForm>) =
    DiscoverSliderScreen(state = state, events = emptyFlow(), actions = noActions(), onDelete = {})
