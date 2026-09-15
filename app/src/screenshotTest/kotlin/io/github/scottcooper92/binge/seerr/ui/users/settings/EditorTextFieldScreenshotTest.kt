package io.github.scottcooper92.binge.seerr.ui.users.settings

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
 * The editor field in the three shapes a form puts it in, so the eye a masked field now carries is
 * judged beside the plain one it sits next to.
 *
 * Only the masked state is framed. Whether it is revealed is the field's own `remember`, which a
 * preview cannot seed; `EditorTextFieldRevealTest` covers the flip.
 */
class EditorTextFieldScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun fields() {
        Column(
            modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            EditorTextField(value = "seerr.example.com", label = "Hostname", onValueChange = {})
            EditorTextField(value = "b4d455k3y", label = "API key", secret = true, onValueChange = {})
            EditorTextField(value = "b4d455k3y", label = "API key", secret = true, enabled = false, onValueChange = {})
        }
    }
}
