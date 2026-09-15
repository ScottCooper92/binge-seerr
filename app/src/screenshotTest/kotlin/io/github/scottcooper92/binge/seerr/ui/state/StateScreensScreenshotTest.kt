package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.ListRowSkeletonColumn
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import com.binge.designsystem.R as DesR

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

    /**
     * What the requests, issues and blocklist roots show while their first page loads. One frame
     * rather than three: all three pass the same two arguments to the same shared component, so a
     * second frame would re-render this one under another name.
     *
     * The users root is deliberately absent — its rows are avatar-leading and shorter than this
     * column reserves, so it keeps the spinner until a skeleton that matches it exists.
     */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun listRootSkeleton() =
        ListRowSkeletonColumn(
            contentPadding = PaddingValues(dimensionResource(DesR.dimen.padding_m)),
        )
}
