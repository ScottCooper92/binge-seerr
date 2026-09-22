package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews

/** The same question on a television, the first answer holding the arrival focus. */
class TvConsentScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Consent() {
        TvConsentScreen(onChoice = {})
    }
}
