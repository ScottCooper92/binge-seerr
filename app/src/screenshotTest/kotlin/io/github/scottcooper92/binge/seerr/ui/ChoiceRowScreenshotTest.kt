package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.ui.state.SortContent
import com.binge.designsystem.R as DesR

private val ROW_WIDTH = 360.dp
private val QUALITY_PROFILES = (1..12).map { it to "Profile $it" }

/**
 * [ChoiceRow], the fix for #336: a row per field, whatever the server's config offers it. [manyOptions]
 * is the frame that matters — twelve profiles is what a real Sonarr answered with in the linked report,
 * and its row is the same height as the one-option row above it. The old chip stack grew with that
 * count; this shape does not.
 *
 * The sheet itself does not capture (modal windows don't), so [sheetContent] frames [SortContent]
 * directly — the stateless body `ChoiceRow` opens — carrying the same twelve profiles the row hides.
 */
class ChoiceRowScreenshotTest {
    /** A set choice, an unset one, and a disabled row — the state a save in flight puts every row in. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun rows() {
        Column(
            modifier = Modifier.width(ROW_WIDTH).padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            ChoiceRow(title = "Root folder", choices = listOf("/data/media" to "/data/media"), selected = "/data/media", onSelect = {})
            ChoiceRow(title = "Language profile", choices = QUALITY_PROFILES, selected = null, onSelect = {})
            ChoiceRow(title = "Quality profile", choices = QUALITY_PROFILES, selected = 1, onSelect = {}, enabled = false)
        }
    }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun manyOptions() {
        Column(
            modifier = Modifier.width(ROW_WIDTH).padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            ChoiceRow(title = "Root folder", choices = listOf("/data/media" to "/data/media"), selected = "/data/media", onSelect = {})
            ChoiceRow(title = "Quality profile", choices = QUALITY_PROFILES, selected = 3, onSelect = {})
        }
    }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sheetContent() {
        SortContent(
            title = "Quality profile",
            choices = QUALITY_PROFILES.map { it.first },
            selected = 3,
            label = { id -> QUALITY_PROFILES.first { it.first == id }.second },
            onSelect = {},
        )
    }
}
