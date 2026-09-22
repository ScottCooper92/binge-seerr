package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews

private val ROW_WIDTH = 411.dp

/**
 * The phone blocklist row (`BlocklistRow`/`BlocklistRowMeta` in `BlocklistList.kt`), which had no frame
 * exercising its own tag-line crowding (#419) — several long tag labels in the row's `FlowRow`, alongside
 * the title, media-type tag, year and blocked-by line.
 */
class BlocklistListScreenshotTest {
    /** Several tags plus a long blocker name and a trailing "Unblock" button on one row. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun manyTags() = Frame(manyTagsBlocklistItem(), canManage = true)

    /** No manage permission: the trailing button is gone, so the content column reads at full width. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun readOnly() = Frame(readOnlyBlocklistItem(), canManage = false)
}

@Composable
private fun Frame(
    item: BlocklistItem,
    canManage: Boolean,
) {
    Box(Modifier.width(ROW_WIDTH).background(MaterialTheme.colorScheme.background)) {
        BlocklistRow(
            item = item,
            isActing = false,
            onClick = {},
            onRemove = if (canManage) ({}) else null,
        )
    }
}
