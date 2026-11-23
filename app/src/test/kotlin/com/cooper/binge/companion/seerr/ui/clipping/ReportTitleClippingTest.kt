package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.handoff.ReportTitleActions
import com.cooper.binge.companion.seerr.ui.handoff.ReportTitleContent
import com.cooper.binge.companion.seerr.ui.handoff.ReportTitleUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_FILM = "Alexandria Fitzgerald-Montgomery and the Rather Long Title of a Film With a Subtitle"
private const val LONG_SERVER = "a-rather-long-hostname.living-room.example.com:5055"

/**
 * What the phone shows for a scanned TV code, with a long title and long server names, at font scales 1.3 and 2.0, through the
 * design system's `assertNoTextClipped`. The body is rendered inline, as the screenshot frames do (#1511).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportTitleClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the report notes at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { ReportNotes() }

    @Test fun `the report notes at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { ReportNotes() }

    @Composable
    private fun ReportNotes() {
        Column {
            listOf(
                ReportTitleUiState.Ready(title = LONG_FILM, is4k = true, mediaId = 5),
                ReportTitleUiState.DifferentServer(theirs = LONG_SERVER, yours = LONG_SERVER),
                ReportTitleUiState.SignedOut,
                ReportTitleUiState.CannotReport,
                ReportTitleUiState.NotTracked(LONG_FILM),
                ReportTitleUiState.Error(SeerrError.Unreachable),
            ).forEach { ReportTitleContent(state = it, actions = ReportTitleActions()) }
        }
    }
}
