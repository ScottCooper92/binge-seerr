package io.github.scottcooper92.binge.seerr.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSheetCallbacks

private val SHEET_WIDTH = 411.dp

/**
 * The proposed manage sheet (debug builds' prototype), one frame per scenario, on the sheet's own
 * container colour at a phone's width — the same framing as the current sheet's frames.
 */
class RequestActionsPrototypeScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun prototypePending() = Prototype(ManageSheetScenario.PendingModerator)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun prototypeOwn() = Prototype(ManageSheetScenario.PendingOwn)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun prototypeFailed() = Prototype(ManageSheetScenario.Failed)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun prototypeAvailable() = Prototype(ManageSheetScenario.Available)
}

@Composable
private fun Prototype(scenario: ManageSheetScenario) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        RequestActionsPrototypeContent(
            model = scenario.model(),
            callbacks = RequestSheetCallbacks(onApprove = {}, onRetry = {}, onDecline = {}, onRemove = {}),
            blockTitle = false,
            onBlockTitleChange = {},
        )
    }
}
