package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.tip.TipUiState
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus

private val SampleApp =
    AppSettings(
        bugReportUrl = "https://github.com/ScottCooper92/binge-seerr/issues/new?template=bug.yml",
        shakeToReport = true,
        binge = BingeStatus.Connected,
    )

/** This app's own page: Binge, bug reporting, what it may report on its own, the tip jar, and About. */
class ThisAppScreenshotTest {
    /** Binge connected and the tip jar priced, which is the page at its fullest. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun ready() =
        ThisAppFrame(ThisAppUiState.Ready(SampleApp), tip = TipUiState.Ready(mapOf(3 to "£3", 5 to "£5", 10 to "£10", 20 to "£20")))

    /** Without Play, or before the products are live, there is no tip row. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun withoutTipJar() = ThisAppFrame(ThisAppUiState.Ready(SampleApp.copy(binge = BingeStatus.NotInstalled)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = ThisAppFrame(ThisAppUiState.Loading)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun fontScales() = ThisAppFrame(ThisAppUiState.Ready(SampleApp))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = ThisAppFrame(ThisAppUiState.Ready(SampleApp))
}

@Composable
private fun ThisAppFrame(
    state: ThisAppUiState,
    tip: TipUiState = TipUiState.Unavailable,
) = ThisAppScreen(
    state = state,
    actions =
        ThisAppActions(
            onBack = {},
            onToggleShakeToReport = {},
            onToggleShareUsageData = {},
            onToggleSendCrashReports = {},
        ),
    tip = tip,
)
