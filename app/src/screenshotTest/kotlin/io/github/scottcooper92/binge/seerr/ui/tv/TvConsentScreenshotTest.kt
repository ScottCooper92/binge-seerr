package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvFontScaleScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrTvSpanishScreenPreviews

/**
 * The same question on a television, the first answer holding the arrival focus. Spanish and a 1.5 font scale run
 * longer than the window, so the page scrolls rather than cutting a point short (#1258).
 */
class TvConsentScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Consent() {
        TvConsentScreen(onChoice = {})
    }

    @PreviewTest
    @SeerrTvSpanishScreenPreviews
    @Composable
    fun ConsentSpanish() {
        TvConsentScreen(onChoice = {})
    }

    @PreviewTest
    @SeerrTvFontScaleScreenPreviews
    @Composable
    fun ConsentLargeFont() {
        TvConsentScreen(onChoice = {})
    }
}
