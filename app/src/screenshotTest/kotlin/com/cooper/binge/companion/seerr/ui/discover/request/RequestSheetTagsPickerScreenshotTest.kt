package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.ui.DestinationIcons
import com.cooper.binge.companion.seerr.ui.requests.DestinationPickerPanel
import com.cooper.binge.companion.seerr.core.ui.R as UiR

private val PAGE_HEIGHT = 720.dp
private val WORST_HEIGHT = 1100.dp
private const val LONG_LIST = 30
private const val LONG_TAG = "A tag the administrator named at considerable length so that it cannot fit on a single line"
private val SHORT_TAGS = listOf("4K", "Anime", "Kids", "Weekend").mapIndexed { index, label -> RequestChoice(index + 1, label) }
private val LONG_NAMED_TAGS =
    listOf(LONG_TAG, "Anime", "$LONG_TAG, twice over", "Kids").mapIndexed { index, label -> RequestChoice(index + 1, label) }

private fun servers(count: Int) = (1..count).map { it to if (it == 1) "Sonarr (default)" else "Sonarr $it" }

private fun longServers() = (1..LONG_LIST).map { it to if (it == 2) LONG_TAG else "Server number $it" }

@Composable
private fun TagsPage(
    tags: List<RequestChoice>,
    selected: Set<Int>,
    enabled: Boolean = true,
    height: Dp = PAGE_HEIGHT,
) {
    SheetFrame(Modifier.height(height)) {
        RequestTagsPage(tags = tags, selected = selected, enabled = enabled, onToggle = {}, onBack = {})
    }
}

@Composable
private fun Picker(
    choices: List<Pair<Int, String>>,
    selected: Int?,
    height: Dp = PAGE_HEIGHT,
) {
    SheetFrame(Modifier.height(height)) {
        DestinationPickerPanel(
            icon = DestinationIcons.server,
            title = stringResource(UiR.string.advanced_server),
            choices = choices,
            selected = selected,
            onSelect = {},
            onBack = {},
        )
    }
}

/** The request sheet's Tags page and the destination picker it swaps in for the server, profile and folder fields. */
class RequestSheetTagsPickerScreenshotTest {
    /** A few tags with two ticked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun tagsSomeTicked() = TagsPage(SHORT_TAGS, selected = setOf(1, 3))

    /** The same page while a send is in flight: the rows are disabled and the ticks stay. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun tagsSending() = TagsPage(SHORT_TAGS, selected = setOf(1, 3), enabled = false)

    /** Long tag names at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun tagsLargeFonts() = TagsPage(LONG_NAMED_TAGS, selected = setOf(1), height = WORST_HEIGHT)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun tagsSpanish() = TagsPage(LONG_NAMED_TAGS, selected = setOf(1), height = WORST_HEIGHT)

    /** A short list with one choice selected. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun pickerShort() = Picker(servers(3), selected = 1)

    /** A list long enough to scroll, so the header holds and the list is cut by the sheet's height. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun pickerLong() = Picker(longServers(), selected = 2)

    /** The longest names at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun pickerLargeFonts() = Picker(longServers(), selected = 2, height = WORST_HEIGHT)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun pickerSpanish() = Picker(longServers(), selected = 2, height = WORST_HEIGHT)
}
