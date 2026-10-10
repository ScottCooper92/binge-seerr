package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R

/**
 * TMDB keywords as a list row: the ones chosen, named once their names are read ([onLoadNames]), else counted. A tap
 * opens the picker ([onOpen]): the blocklisted tags page, which saves them itself, or the rule's own search, which hands
 * them back to the rule. [emptyLabel] is what none chosen reads.
 */
@Composable
internal fun keywordSettingItem(
    icon: ImageVector,
    title: String,
    chosen: List<Int>,
    names: Map<Int, String>,
    enabled: Boolean,
    onLoadNames: (List<Int>) -> Unit,
    onOpen: () -> Unit,
    emptyLabel: String = stringResource(R.string.server_settings_blocklist_tags_none),
): ListItem {
    LaunchedEffect(chosen) { onLoadNames(chosen) }
    val named = chosen.mapNotNull { names[it] }
    return ListItem(
        icon = icon,
        label = title,
        detail =
            when {
                chosen.isEmpty() -> emptyLabel
                named.size == chosen.size -> named.joinToString(", ")
                else -> pluralStringResource(R.plurals.server_settings_blocklist_tags_count, chosen.size, chosen.size)
            },
        clickable = enabled,
        disabled = !enabled,
        onClick = onOpen,
    )
}
