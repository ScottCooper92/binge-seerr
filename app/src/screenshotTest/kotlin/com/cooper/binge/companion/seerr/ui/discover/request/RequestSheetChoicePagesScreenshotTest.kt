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

private val PAGE_HEIGHT = 640.dp
private val WORST_HEIGHT = 1000.dp
private const val LONG_TAG = "A tag whose name runs well past the width of a phone sheet"

private fun tags(longName: Boolean = false) =
    listOf(
        RequestChoice(1, if (longName) LONG_TAG else "4k"),
        RequestChoice(2, "anime"),
        RequestChoice(3, "kids"),
        RequestChoice(4, "hdr"),
        RequestChoice(5, "foreign"),
    )

private fun profiles(count: Int) = (1..count).map { it to "Quality profile $it, 1080p Bluray and WEB" }

@Composable
private fun TagsPage(
    selected: Set<Int>,
    enabled: Boolean = true,
    longName: Boolean = false,
    height: Dp = PAGE_HEIGHT,
) {
    SheetFrame(Modifier.height(height)) {
        RequestTagsPage(tags(longName), selected, enabled, onToggle = {}, onBack = {})
    }
}

@Composable
private fun Picker(
    count: Int,
    selected: Int,
    height: Dp = PAGE_HEIGHT,
) {
    SheetFrame(Modifier.height(height)) {
        DestinationPickerPanel(
            icon = DestinationIcons.profile,
            title = stringResource(UiR.string.advanced_profile),
            choices = profiles(count),
            selected = selected,
            onSelect = {},
            onBack = {},
        )
    }
}

/** The request sheet's Tags page and its destination picker, the two pages the form swaps in for its server fields. */
class RequestSheetChoicePagesScreenshotTest {
    /** Some tags, a few ticked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun tags() = TagsPage(selected = setOf(2, 4))

    /** The same page while a send is in flight: the boxes are disabled. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun tagsSending() = TagsPage(selected = setOf(2, 4), enabled = false)

    /** A short list of choices, the current one marked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun pickerShort() = Picker(count = 3, selected = 2)

    /** A list longer than the sheet, which scrolls itself. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun pickerScrolling() = Picker(count = 16, selected = 9)

    /** The longest names at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun tagsLargeFonts() = TagsPage(selected = setOf(1), longName = true, height = WORST_HEIGHT)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun pickerLargeFonts() = Picker(count = 6, selected = 2, height = WORST_HEIGHT)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun tagsSpanish() = TagsPage(selected = setOf(1), longName = true, height = WORST_HEIGHT)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun pickerSpanish() = Picker(count = 6, selected = 2, height = WORST_HEIGHT)
}
