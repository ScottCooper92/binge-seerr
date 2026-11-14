package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.ThisAppActions
import com.cooper.binge.companion.seerr.ui.settings.ThisAppScreen
import com.cooper.binge.companion.seerr.ui.settings.ThisAppUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_URL = "https://a-rather-long-hostname.living-room.home.example.org:5055/requests/with/a/path"

/**
 * The This app page at its worst: Binge connected, every toggle on and a long bug-report address, with the About group
 * below, at font scales 1.3 and 2.0, through the design system's `assertNoTextClipped`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThisAppClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) = rule.assertNoClippingAt(fontScale) { ThisAppScreen(state = worstCase(), actions = noActions()) }

    @Test fun `the this app page at 1_3`() = check(FONT_SCALE_13)

    @Test fun `the this app page at 2_0`() = check(FONT_SCALE_20)

    private fun worstCase() =
        ThisAppUiState.Ready(
            AppSettings(
                bugReportUrl = LONG_URL,
                shakeToReport = true,
                shareUsageData = true,
                sendCrashReports = true,
                binge = BingeStatus.Connected,
            ),
        )

    private fun noActions() =
        ThisAppActions(
            onBack = {},
            onToggleShakeToReport = {},
            onToggleShareUsageData = {},
            onToggleSendCrashReports = {},
            onOpenLicences = {},
        )
}
