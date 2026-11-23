package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.runtime.Composable
import com.cooper.binge.companion.seerr.ui.settings.server.MediaServerActions
import com.cooper.binge.companion.seerr.ui.settings.server.PlexConnection
import com.cooper.binge.companion.seerr.ui.settings.server.PlexServerChoice
import com.cooper.binge.companion.seerr.ui.settings.server.PlexServerChoices
import com.cooper.binge.companion.seerr.ui.settings.server.PlexServerPicker
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_HOST = "a-rather-long-hostname.living-room.home.example.org"

/**
 * The Plex server choices sheet's body with a long server name, a long address and every part of a connection's detail
 * line, at font scales 1.3 and 2.0, through the design system's `assertNoTextClipped`. The sheet is a modal window, so its
 * body is rendered inline, as its screenshot frame does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlexServerChoicesClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the server choices at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Choices() }

    @Test fun `the server choices at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Choices() }

    @Composable
    private fun Choices() =
        PlexServerChoices(
            PlexServerPicker.Ready(
                listOf(
                    PlexServerChoice(
                        "The Upstairs Living Room Plex Media Server",
                        listOf(
                            PlexConnection("192.168.100.200", 32400, useSsl = false, local = true, reachable = true),
                            PlexConnection(LONG_HOST, 32400, useSsl = true, local = false, reachable = false),
                        ),
                    ),
                ),
            ),
            MediaServerActions(
                onSetLibraryEnabled = { _, _ -> },
                onSyncLibraries = {},
                onStartScan = {},
                onCancelScan = {},
                onOpenServerPicker = {},
                onCloseServerPicker = {},
                onChooseConnection = { _, _ -> },
                onOpenTautulli = {},
            ),
        )
}
