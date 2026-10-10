package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.PHONE_WIDTH
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews

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

    /** The Unblock in flight: the spinner takes the glyph's place and the row is disabled. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun unblocking() = Frame(manyTagsBlocklistItem(), canManage = true, isActing = true)

    /** At 1.5 and 2.0 font scale the icon button stays 48dp, so the title keeps what the button leaves. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun fontScale() = Frame(manyTagsBlocklistItem(), canManage = true)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = Frame(manyTagsBlocklistItem(), canManage = true)

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
    isActing: Boolean = false,
) {
    Box(Modifier.width(PHONE_WIDTH).background(MaterialTheme.colorScheme.background)) {
        BlocklistRow(
            item = item,
            isActing = isActing,
            onClick = {},
            onRemove = if (canManage) ({}) else null,
        )
    }
}
