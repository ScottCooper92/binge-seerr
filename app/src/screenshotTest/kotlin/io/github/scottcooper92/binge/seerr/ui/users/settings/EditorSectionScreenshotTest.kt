package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews
import com.binge.designsystem.R as DesR

/**
 * The collapsible section on its own: closed, open, and held open by an issue. The instance form's own
 * frames show it in a page; these hold the header and the field messages at the sizes and in the
 * language that stress them.
 */
class EditorSectionScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun collapsedAndExpanded() = Frame(issues = emptyList())

    /** An invalid value in the first section and a missing one in the second, once Save has been tried. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun invalidSectionsOpenThemselves() = Frame(issues = ISSUES)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = Frame(issues = ISSUES)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun largeText() = Frame(issues = ISSUES)
}

private val ISSUES =
    listOf(
        invalid("open", "port", R.string.editor_error_port),
        missing("closed", "name"),
        missing("closed", "host"),
    )

@Composable
private fun Frame(issues: List<EditorIssue>) {
    CompositionLocalProvider(LocalEditorIssues provides issues) {
        Column(
            modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            EditorSection("open", "Connection") {
                EditorTextField(value = "78x8", label = "Port", fieldId = "port", required = true, onValueChange = {})
                EditorTextField(value = "radarr.lan", label = "Host", onValueChange = {})
            }
            EditorSection("closed", "Destination", defaultExpanded = false) {
                EditorTextField(value = "", label = "Name", fieldId = "name", required = true, onValueChange = {})
                EditorTextField(value = "", label = "Host", fieldId = "host", required = true, onValueChange = {})
            }
            EditorSection("optional", "Advanced", defaultExpanded = false) {
                EditorTextField(value = "", label = "URL base", onValueChange = {})
            }
        }
    }
}
