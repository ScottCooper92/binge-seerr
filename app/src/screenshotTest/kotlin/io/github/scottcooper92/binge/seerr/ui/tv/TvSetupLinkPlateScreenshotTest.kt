package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.LinkFlow

/**
 * The first television frames in this suite, and the reason the locale-pinned TV multipreview
 * exists: on the design system's own `@TvPreviews` these would record the en-XA pseudolocale.
 *
 * A sign-in that finishes elsewhere is all a remote can do with these two, so the code and the copy
 * telling the user where to take it are the whole screen. Both flows are framed because they differ
 * only in that copy, which is exactly the part a frame can check and a focus test cannot.
 */
class TvSetupLinkPlateScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun quickConnect() = TvSetupLinkPlate(link = LinkFlow.QuickConnect(code = "472913"), onCancel = {})

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun plex() =
        TvSetupLinkPlate(
            link = LinkFlow.Plex(code = "JKLM", authUrl = "https://app.plex.tv/auth", launchPending = false),
            onCancel = {},
        )
}
