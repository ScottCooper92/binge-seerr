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

/** A form as it now reads: an untitled lead card, then titled cards, each holding its own fields. */
class EditorSectionCardScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun cards() {
        Column(
            modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            EditorSectionCard(title = "General") {
                EditorSwitchRow(label = "Trust proxy", checked = true, onToggle = {})
                EditorSwitchRow(label = "CSRF protection", checked = false, onToggle = {})
            }
            EditorSectionCard(title = "Proxy") {
                EditorSwitchRow(label = "Enable proxy", checked = true, onToggle = {})
                EditorTextField(value = "proxy.example.com", label = "Hostname", onValueChange = {})
                EditorTextField(value = "b4d455k3y", label = "Password", secret = true, onValueChange = {})
            }
        }
    }
}
