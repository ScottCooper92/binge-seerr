package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError

/**
 * The three whole-screen states every ported screen renders around its content, and the first frames
 * in this repository.
 *
 * They are the submodule canary this suite exists for: all three are built entirely from shared
 * design-system components, so a bump that changes the loading indicator, the plate or the filled
 * button shows up here rather than silently on a user's screen.
 */
class StateScreensScreenshotTest {
    /** The canonical layout for this family: a centred plate, asked once across the device matrix. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun error() = ErrorScreen(error = SeerrError.Unreachable)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun errorWithRetry() = ErrorScreen(error = SeerrError.Server, onRetry = {})

    /** Unauthorized picks a different glyph and copy, which is the branch worth a frame of its own. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun errorUnauthorized() = ErrorScreen(error = SeerrError.Unauthorized)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = LoadingScreen()

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun empty() = EmptyScreen(message = "No requests match this filter.")
}
