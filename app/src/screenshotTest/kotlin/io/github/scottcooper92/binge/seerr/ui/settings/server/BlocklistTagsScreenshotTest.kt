package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews

/** Settings › General › Blocklisted tags: the tags as chips under Material's search bar, or the empty state. */
class BlocklistTagsScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun tags() = TagsFrame(tagged())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun empty() = TagsFrame(BlocklistTagsUiState.Ready(tags = emptyList()))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = TagsFrame(tagged())
}

private fun tagged() =
    BlocklistTagsUiState.Ready(
        tags = listOf(9951, 210024, 4344, 818, 6075),
        names = mapOf(9951 to "kaiju", 210024 to "anime", 4344 to "musical", 818 to "based on novel or book", 6075 to "sport"),
    )

@Composable
private fun TagsFrame(state: BlocklistTagsUiState) =
    BlocklistTagsScreen(
        state = state,
        actions = BlocklistTagsActions(onBack = {}, onSearch = {}, onToggle = {}, onRetry = {}, onReload = {}),
    )
