package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.TagRow
import com.binge.designsystem.resolvedContentInset
import com.cooper.binge.companion.seerr.discover.title.TitleKeyword
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13ComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews

private val KEYWORDS =
    listOf("simulation", "dystopia", "martial arts", "artificial intelligence", "post-apocalyptic future", "cyberpunk")
        .mapIndexed { index, name -> TitleKeyword(index + 1, name) }

@Composable
private fun KeywordTags() =
    TagRow(KEYWORDS.map { it.name }, Modifier.padding(horizontal = resolvedContentInset()), compact = true, onTagClick = {})

/**
 * The keyword chips on a film's or a series' page, which share this row, framed on their own so the larger text
 * cannot scroll them out of the frame: the chips wrap onto more lines and none is cut off. The heading is left out,
 * because a component frame crops to its content and would clip it.
 */
class KeywordsSectionScreenshotTest {
    /** At font scale 2, the largest the app is checked at. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun chipsLargestText() = KeywordTags()

    /** At font scale 1.3, the size a phone's "larger text" setting commonly lands on, in the dark and the light theme. */
    @PreviewTest
    @SeerrFontScale13ComponentPreviews
    @Composable
    fun chipsLargerText() = KeywordTags()
}
