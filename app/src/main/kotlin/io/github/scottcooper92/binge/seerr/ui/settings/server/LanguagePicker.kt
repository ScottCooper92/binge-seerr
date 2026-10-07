package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.CheckboxRow
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.TextEntrySurface
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.PeekingListSheet
import java.util.Locale
import com.binge.designsystem.R as DesR

/** The server keeps a discover-language filter as codes joined by `|`: `en|ja`. Blank is no filter. */
internal fun String.languageCodes(): List<String> = split('|').map { it.trim() }.filter { it.isNotEmpty() }

/** A language named in the device's language, falling back to TMDB's English name, then its code. */
internal fun languageName(
    code: String,
    englishName: String? = null,
): String =
    Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?: englishName?.takeIf { it.isNotBlank() }
        ?: code

/**
 * What a language filter's checklist shows: a saved code the list lacks stays (so Done never quietly drops it), the
 * chosen ones lead, and the rest follow by name. [filter] narrows by name or code.
 */
internal fun languageChecklist(
    entries: List<ListEntry>,
    initial: List<String>,
    filter: String,
): List<Pair<String, String>> {
    val named = entries.map { it.code to languageName(it.code, it.englishName) }
    val all = initial.filter { code -> named.none { it.first == code } }.map { it to it } + named
    return all
        .filter { (code, name) ->
            filter.isBlank() ||
                name.contains(filter.trim(), ignoreCase = true) ||
                code.equals(filter.trim(), ignoreCase = true)
        }.sortedWith(compareBy({ it.first !in initial }, { it.second.lowercase() }))
}

/**
 * A language filter as a list row: the languages chosen, named on the device, so the page needs no list to draw it.
 * The server's list is read only when the sheet opens ([onOpen]). The sheet is a checklist with a search field, its
 * Clear and Done in the header at either height; Done applies the picks.
 */
@Composable
internal fun languageSettingItem(
    icon: ImageVector,
    title: String,
    value: String,
    choices: ListChoices?,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSelect: (String) -> Unit,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    val chosen = value.languageCodes()
    if (open) {
        val apply = { codes: List<String> ->
            onSelect(codes.joinToString("|"))
            open = false
        }
        // Held here, not in the list, so the header's Clear and Done act on it at either height.
        var picked by rememberSaveable { mutableStateOf(chosen) }
        PeekingListSheet(
            title = title,
            onDismiss = { open = false },
            actions = {
                if (choices is ListChoices.Ready) {
                    BingeTextButton(label = stringResource(R.string.server_settings_list_clear), onClick = { picked = emptyList() })
                    BingeTextButton(label = stringResource(R.string.editor_done), onClick = { apply(picked) })
                }
            },
        ) {
            when (choices) {
                is ListChoices.Ready -> LanguageChecklist(choices.entries, chosen, picked, onPicked = { picked = it })
                ListChoices.Failed -> LanguagesUnavailable(value, onRetry = onOpen, onUse = { apply(it.languageCodes()) })
                ListChoices.Loading, null -> ListLoading()
            }
        }
    }
    return ListItem(
        icon = icon,
        label = title,
        detail =
            if (chosen.isEmpty()) {
                stringResource(
                    R.string.server_settings_all_languages,
                )
            } else {
                chosen.joinToString(", ") { languageName(it) }
            },
        clickable = enabled,
        disabled = !enabled,
        onClick = {
            open = true
            onOpen()
        },
    )
}

@Composable
internal fun LanguageChecklist(
    entries: List<ListEntry>,
    initial: List<String>,
    picked: List<String>,
    onPicked: (List<String>) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = remember(entries, initial, filter) { languageChecklist(entries, initial, filter) }
    BingeSearchField(
        query = filter,
        onQueryChange = { filter = it },
        onClear = { filter = "" },
        placeholder = stringResource(R.string.server_settings_filter_languages),
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
    )
    shown.forEachIndexed { index, (code, name) ->
        CheckboxRow(
            label = name,
            checked = code in picked,
            onToggle = { on -> onPicked(if (on) picked + code else picked - code) },
            showDivider = index < shown.lastIndex,
        )
    }
}

/** The server couldn't send its languages: try again, or type codes joined by `|` as the field used to take them. */
@Composable
private fun LanguagesUnavailable(
    value: String,
    onRetry: () -> Unit,
    onUse: (String) -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf(value) }
    TextEntrySurface(
        title = stringResource(R.string.server_settings_language_codes),
        value = typed,
        onValueChange = { typed = it },
        onSubmit = { onUse(typed.trim()) },
        onCancel = onRetry,
        submitLabel = stringResource(R.string.server_settings_list_use),
        cancelLabel = stringResource(R.string.action_try_again),
        submitEnabled = true,
        minLines = 1,
        maxLines = 1,
        header = {
            Text(
                stringResource(R.string.server_settings_languages_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
