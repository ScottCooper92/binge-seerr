package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Settings › Logs. The lines are paged, and `LazyPagingItems` never leaves loading in a static frame
 * (see `UserDetailScreenshotTest`), so the page frames show the chrome (the search field and the level
 * chips) over a page that is loading; the lines themselves are framed on their own below.
 */
class LogsScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun chromeLayout() = LogsFrame(LogsUiState())

    /** A level picked and a search typed: the chip moves and the field holds the text. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun errorLevelSearching() = LogsFrame(LogsUiState(level = LogLevel.Error, search = "sonarr"))
}

/** One line at each level, with and without attached data, and opened. */
class LogRowScreenshotTest {
    /** The four levels in their tones; a line with no attached data has nothing to open. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun levels() =
        RowsFrame {
            LogLevel.entries.forEach { level ->
                LogRow(entry(level = level, message = "Sample ${level.apiValue} line from the server"), onCopy = {})
            }
        }

    /** A line carrying data, closed; it is the row that opens. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun withData() = RowsFrame { LogRow(entry(level = LogLevel.Warn, data = DATA), onCopy = {}) }

    /** The same line opened to what it carried. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun opened() = RowsFrame { LogRow(entry(level = LogLevel.Error, data = DATA), onCopy = {}, initiallyExpanded = true) }

    /** A long message is held to three lines; the chevron that opens it appears after the first layout, which a static frame never reaches. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun longMessage() = RowsFrame { LogRow(entry(level = LogLevel.Error, message = LONG_MESSAGE), onCopy = {}) }

    /** A line with no label and a time that did not parse shows the server's own timestamp. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun unlabelled() = RowsFrame { LogRow(entry(level = LogLevel.Info, label = null, message = "Server started"), onCopy = {}) }
}

private val LONG_MESSAGE = "Request failed after retrying: " + "the instance did not answer within the timeout. ".repeat(6)

private const val DATA = "{\n  \"errorMessage\": \"connect ECONNREFUSED 10.0.0.12:8989\",\n  \"status\": 503\n}"

private val ROW_WIDTH = 411.dp
private val ROW_PADDING = 16.dp
private val ROW_SPACING = 12.dp

/** The time is left unparsed so the row shows the server's own timestamp rather than a date that moves with the clock. */
private fun entry(
    level: LogLevel,
    label: String? = "Sonarr",
    message: String = "Unable to reach the instance",
    data: String? = null,
) = LogEntry(
    id = "${level.apiValue}-$message",
    timestampMillis = null,
    timestampRaw = "2026-09-29T21:14:07.000Z",
    level = level,
    label = label,
    message = message,
    data = data,
)

@Composable
private fun RowsFrame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.width(ROW_WIDTH).padding(ROW_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        content()
    }
}

@Composable
private fun LogsFrame(state: LogsUiState) =
    LogsScreen(
        state = state,
        entriesFor = { flowOf(PagingData.from(emptyList())) },
        events = emptyFlow(),
        actions = LogsActions(onBack = {}, onLevelChange = {}, onSearchChange = {}, onFollowingChange = {}, onCopy = {}),
    )
