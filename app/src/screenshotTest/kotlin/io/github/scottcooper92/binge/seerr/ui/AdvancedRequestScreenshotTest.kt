package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview

/**
 * The phone hand-off Binge opens for "request with options": the options form's layout across the
 * device matrix, then its states and each whole-screen failure on the phone cell alone.
 */
class AdvancedRequestScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun readyLayout() = Frame(previewAdvancedReady())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loadingChoices() = Frame(previewAdvancedReady(isLoadingChoices = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun submitting() = Frame(previewAdvancedReady(isSubmitting = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun rejected() = Frame(previewAdvancedReady(error = AdvancedRequestError.Rejected))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(AdvancedRequestUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun submitted() = Frame(AdvancedRequestUiState.Submitted)

    /** Not connected is the one failure with a way forward, so it alone carries the setup button. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failedNotConnected() = Frame(AdvancedRequestUiState.Failed(AdvancedRequestError.NotConnected))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failedNoServers() = Frame(AdvancedRequestUiState.Failed(AdvancedRequestError.NoServers))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failedUnreachable() = Frame(AdvancedRequestUiState.Failed(AdvancedRequestError.Unreachable))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failedUnknown() = Frame(AdvancedRequestUiState.Failed(AdvancedRequestError.Unknown))
}

@Composable
private fun Frame(state: AdvancedRequestUiState) {
    AdvancedRequestScreen(
        state = state,
        onSelectServer = {},
        onSelectProfile = {},
        onSelectRootFolder = {},
        onSubmit = {},
        onOpenSetup = {},
        onClose = {},
    )
}
