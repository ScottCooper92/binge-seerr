package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import com.cooper.binge.companion.seerr.ui.settings.server.LogEntry
import com.cooper.binge.companion.seerr.ui.settings.server.LogLevel
import com.cooper.binge.companion.seerr.ui.settings.server.LogRow
import com.cooper.binge.companion.seerr.ui.settings.server.LogsActions
import com.cooper.binge.companion.seerr.ui.settings.server.LogsScreen
import com.cooper.binge.companion.seerr.ui.settings.server.LogsUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val LONG_MESSAGE = "Request failed after retrying: " + "the instance did not answer within the timeout. ".repeat(6)
private const val DATA = "{\n  \"errorMessage\": \"connect ECONNREFUSED 10.0.0.12:8989\",\n  \"status\": 503\n}"

/**
 * The server's log with every level, a long message, a long label and expanded data, at font scales 1.3 and 2.0,
 * through the design system's `assertNoTextClipped`. A row clips a long message to a few lines on purpose until it is
 * opened; that is an ellipsis, which the assertion passes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h8000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogsClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `log rows at 1_3`() = rule.assertClipFree(FONT_13) { Rows() }

    @Test fun `log rows at 2_0`() = rule.assertClipFree(FONT_20) { Rows() }

    @Test fun `the logs screen at 1_3`() = rule.assertClipFree(FONT_13) { Screen() }

    @Test fun `the logs screen at 2_0`() = rule.assertClipFree(FONT_20) { Screen() }

    private fun entry(
        level: LogLevel,
        message: String = LONG_MESSAGE,
        data: String? = DATA,
    ) = LogEntry(
        id = "${level.apiValue}-${message.length}",
        timestampMillis = null,
        timestampRaw = "2026-09-29T21:14:07.000Z",
        level = level,
        label = "Radarr 4K Remux Library on the Upstairs Server",
        message = message,
        data = data,
    )

    @Composable
    private fun Rows() =
        Column {
            LogLevel.entries.forEach { level -> LogRow(entry(level), onCopy = {}) }
            LogRow(entry(LogLevel.Error), onCopy = {}, initiallyExpanded = true)
        }

    @Composable
    private fun Screen() {
        val rows = remember { flowOf(PagingData.from(LogLevel.entries.map { entry(it, data = null) })) }
        LogsScreen(
            state = LogsUiState(level = LogLevel.Error, search = "a long search phrase that wraps"),
            entriesFor = { rows },
            events = emptyFlow(),
            actions = LogsActions(onBack = {}, onLevelChange = {}, onSearchChange = {}, onFollowingChange = {}, onCopy = {}),
        )
    }
}
