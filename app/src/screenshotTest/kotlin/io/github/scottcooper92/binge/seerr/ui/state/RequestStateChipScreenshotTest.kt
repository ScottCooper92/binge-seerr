package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import com.binge.designsystem.R as DesR

/**
 * Every tone the status pill has, in one frame. The pill is the one piece of chrome repeated down
 * every request and issue list, and its tones are only judgeable against each other.
 */
class RequestStateChipScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun tones() {
        Column(
            modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            RequestStateTone.entries.forEach { tone ->
                RequestStateChip(label = tone.name, tone = tone)
            }
        }
    }
}
