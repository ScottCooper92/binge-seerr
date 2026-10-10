package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.binge.designsystem.template.FormScreen
import io.github.scottcooper92.binge.seerr.R

/**
 * The blocklisted tags page's search and tags, over the rule being edited and handing its picks back to it: a keyword
 * is added or taken out with a tap, through [onToggle], and nothing is saved until the rule is. Back closes it.
 */
@Composable
internal fun KeywordPickerDialog(
    title: String,
    chosen: List<Int>,
    search: KeywordSearch,
    onSearch: (String) -> Unit,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            FormScreen(title = title, onBack = onDismiss, scrolling = false) { inner ->
                Column(modifier = Modifier.fillMaxSize().padding(inner)) {
                    TagSearchPanel(
                        chosen = chosen,
                        search = search,
                        onQuery = onSearch,
                        onToggle = onToggle,
                        emptyTitle = stringResource(R.string.server_settings_rule_keywords_empty_title),
                        emptyBody = stringResource(R.string.server_settings_rule_keywords_empty),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
