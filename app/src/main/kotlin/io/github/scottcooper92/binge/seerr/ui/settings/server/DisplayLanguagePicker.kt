package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.os.LocaleListCompat
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.ListItem
import java.util.Locale

private const val GLOBE = "🌐"

/**
 * The display language as a list row whose sheet is the shared choice sheet. Each language keeps its own name, carries
 * its code as a mark and the device's name for it beneath, and the list runs in the device's names. The server's
 * languages that match the device's are suggested. [choices] are (tag, label) pairs; a blank tag is the server's
 * default, where the page has one.
 */
@Composable
internal fun displayLanguageSettingItem(
    icon: ImageVector,
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open) {
        val listed =
            remember(choices) {
                val (default, languages) = choices.partition { it.first.isBlank() }
                (default + languages.sortedBy { (tag, label) -> deviceName(tag) ?: label }).map { (tag, label) ->
                    BingeChoice(
                        value = tag,
                        label = label,
                        subtitle = deviceName(tag)?.takeIf { it != label },
                        mark = tag.substringBefore('-').uppercase().ifEmpty { GLOBE },
                    )
                }
            }
        BingeChoiceSheet(
            title = title,
            choices = BingeChoiceList.Ready(listed),
            selected = selected,
            onSelect = onSelect,
            onDismiss = { open = false },
            suggested = remember(choices) { deviceDisplayLanguages(choices.map { it.first }) },
        )
    }
    return ListItem(
        icon = icon,
        label = title,
        detail = choices.firstOrNull { it.first == selected }?.second ?: selected,
        clickable = enabled,
        disabled = !enabled,
        onClick = { open = true },
    )
}

/** [tag]'s name in the device's language, or null for the server's default. */
private fun deviceName(tag: String): String? = tag.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it).displayName }

/** The offered tags that match the device's languages, best match first: the exact tag, else the language alone. */
private fun deviceDisplayLanguages(offered: List<String>): List<String> {
    val locales = LocaleListCompat.getAdjustedDefault()
    return (0 until locales.size())
        .mapNotNull { locales[it] }
        .flatMap { locale ->
            listOfNotNull(
                offered.firstOrNull { it.equals(locale.toLanguageTag(), ignoreCase = true) },
                offered.firstOrNull { it.equals(locale.language, ignoreCase = true) },
            )
        }.distinct()
}
