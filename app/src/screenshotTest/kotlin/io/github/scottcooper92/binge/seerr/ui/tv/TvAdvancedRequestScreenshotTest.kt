package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestError
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestUiState

private val NoAdvancedActions = TvAdvancedRequestActions({}, {}, {}, {}, {}, {})

/**
 * Every arm of the television hand-off: the options form with the focus where a remote can put it, the
 * form while its choices load, refused and submitting, and the three whole-screen plates. The states
 * come from `TvPreviewData.kt`'s builders, so the IDE preview and the frame cannot drift.
 */
class TvAdvancedRequestScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ready() = TvAdvancedRequestScreen(state = advancedReady(), actions = NoAdvancedActions, initialFocusedLabel = "Radarr")

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun submitFocused() = TvAdvancedRequestScreen(state = advancedReady(), actions = NoAdvancedActions, initialSubmitFocused = true)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun loadingChoices() = TvAdvancedRequestScreen(state = advancedReady(isLoadingChoices = true), actions = NoAdvancedActions)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun rejected() = TvAdvancedRequestScreen(state = advancedReady(error = AdvancedRequestError.Rejected), actions = NoAdvancedActions)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun submitting() = TvAdvancedRequestScreen(state = advancedReady(isSubmitting = true), actions = NoAdvancedActions)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun notConnected() =
        TvAdvancedRequestScreen(state = AdvancedRequestUiState.Failed(AdvancedRequestError.NotConnected), actions = NoAdvancedActions)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun unreachable() =
        TvAdvancedRequestScreen(state = AdvancedRequestUiState.Failed(AdvancedRequestError.Unreachable), actions = NoAdvancedActions)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun loading() = TvAdvancedRequestScreen(state = AdvancedRequestUiState.Loading, actions = NoAdvancedActions)

    /** The hand-off after Submit: the same plate as loading, with the submitting copy. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun submitted() = TvAdvancedRequestScreen(state = AdvancedRequestUiState.Submitted, actions = NoAdvancedActions)
}
