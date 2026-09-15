package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode

private val SHEET_WIDTH = 411.dp

/**
 * The first sheet body in this suite. The modal window does not capture, so the frame renders the
 * stateless content on the sheet's own container colour at a phone's width.
 *
 * Both instances are framed because the second line is the whole point of the header: on a title the
 * server holds twice, it is the only thing saying which of the two is being marked.
 */
class MediaStatusSheetScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun standard() = Frame(is4k = false, status = SeerrMediaStatusCode.Available)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun fourK() = Frame(is4k = true, status = SeerrMediaStatusCode.Processing)
}

@Composable
private fun Frame(
    is4k: Boolean,
    status: SeerrMediaStatusCode,
) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        MediaStatusSheetContent(
            instance =
                MediaInstance(
                    is4k = is4k,
                    status = status,
                    serviceUrl = null,
                    mediaServerUrl = null,
                    watch = null,
                ),
            onSelect = {},
        )
    }
}
