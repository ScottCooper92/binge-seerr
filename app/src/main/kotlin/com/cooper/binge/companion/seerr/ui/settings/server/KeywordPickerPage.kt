package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.cooper.binge.companion.seerr.R

/**
 * The blocklisted tags page's search and tags as a page over the editor being edited, handing its picks to it: a
 * keyword is added or taken out with a tap, through [onToggle], and the editor holds them. Back leaves.
 */
@Composable
internal fun KeywordPickerPage(
    title: String,
    chosen: List<Int>,
    search: KeywordSearch,
    onSearch: (String) -> Unit,
    onToggle: (Int) -> Unit,
    onBack: () -> Unit,
) {
    FormScreen(title = title, onBack = onBack, bar = ScreenBar.Small, scrolling = false) { inner ->
        Column(modifier = Modifier.fillMaxSize()) {
            TagSearchPanel(
                chosen = chosen,
                search = search,
                onQuery = onSearch,
                onToggle = onToggle,
                emptyTitle = stringResource(R.string.server_settings_rule_keywords_empty_title),
                emptyBody = stringResource(R.string.server_settings_rule_keywords_empty),
                modifier = Modifier.weight(1f),
                contentPadding = inner,
            )
        }
    }
}
