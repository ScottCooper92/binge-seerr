package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeMultiChoiceSheet
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.TextEntrySurface
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.LanguageCodeShapes
import io.github.scottcooper92.binge.seerr.ui.state.PeekingListSheet
import java.util.Locale

/** The server keeps a discover-language filter as codes joined by `|`: `en|ja`. Blank is no filter. */
internal fun String.languageCodes(): List<String> =
    split('|').map { it.trim() }.filter { it.isNotEmpty() && it != ALL_LANGUAGES && it != SERVER_LANGUAGES }

/** A user's "no filter". A blank user filter means the server's, which Overseerr also keeps as `server`. */
internal const val ALL_LANGUAGES = "all"
internal const val SERVER_LANGUAGES = "server"

/** Whether typed codes may be used: blank clears the filter, otherwise every code must be the shape the page saves. */
internal fun languageEntryUsable(typed: String): Boolean =
    typed.languageCodes().let { it.isEmpty() || LanguageCodeShapes.isOriginalLanguage(it.joinToString("|")) }

/** A language named in the device's language, falling back to TMDB's English name, then its code. */
internal fun languageName(
    code: String,
    englishName: String? = null,
): String =
    Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?: englishName?.takeIf { it.isNotBlank() }
        ?: code

/**
 * What a language filter's sheet offers: the list's entries named on the device, by name, plus any saved code the list
 * lacks (so Done never quietly drops it). Leading the chosen ones is the sheet's job.
 */
internal fun languageChecklist(
    entries: List<ListEntry>,
    initial: List<String>,
): List<Pair<String, String>> {
    val named = entries.map { it.code to languageName(it.code, it.englishName) }
    val all = initial.filter { code -> named.none { it.first == code } }.map { it to it } + named
    return all.sortedBy { it.second.lowercase() }
}

/**
 * A language filter as a list row: the languages chosen, named on the device, so the page needs no list to draw it.
 * The server's list is read only when the sheet opens ([onOpen]). The sheet is a [BingeMultiChoiceSheet]: the
 * chosen languages in a Selected section, the device's and popular ones in Suggested, the rest in All, each marked with
 * its code. Clear and Done sit in the header; Done applies the picks. A user's filter passes [serverDefault], the
 * server's own filter: blank then reads "Default (…)", a Default beside Clear and Done in the header's `actions` goes
 * back to it, and Clear keeps [ALL_LANGUAGES] rather than blank. A list the server cannot send falls back to typing
 * codes on a [PeekingListSheet].
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
    serverDefault: String? = null,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    val chosen = value.languageCodes()
    val user = serverDefault != null
    if (open) {
        val apply = { codes: List<String> ->
            onSelect(if (codes.isEmpty() && user) ALL_LANGUAGES else codes.joinToString("|"))
            open = false
        }
        if (choices == ListChoices.Failed) {
            PeekingListSheet(title = title, onDismiss = { open = false }) {
                LanguagesUnavailable(value, onRetry = onOpen, onUse = { apply(it.languageCodes()) })
            }
        } else {
            val listed =
                (choices as? ListChoices.Ready)?.let { ready ->
                    languageChecklist(ready.entries, chosen)
                        .map { (code, name) -> BingeChoice(code, name, mark = code.uppercase()) }
                }
            BingeMultiChoiceSheet(
                title = title,
                choices = listed?.let { BingeChoiceList.Ready(it) } ?: BingeChoiceList.Loading,
                selected = chosen.toSet(),
                // In the list's order, so the saved filter reads the same however the ticks were made.
                onDone = { picked -> apply(listed.orEmpty().map { it.value }.filter { it in picked }) },
                onDismiss = { open = false },
                doneLabel = stringResource(R.string.editor_done),
                clearLabel = stringResource(R.string.server_settings_list_clear),
                suggested = remember { suggestedLanguages() },
                actions = {
                    if (user) {
                        BingeTextButton(
                            label = stringResource(R.string.settings_use_server_default),
                            onClick = {
                                onSelect("")
                                open = false
                            },
                        )
                    }
                },
            )
        }
    }
    return ListItem(
        icon = icon,
        label = title,
        detail = languageDetail(value, chosen, serverDefault),
        clickable = enabled,
        disabled = !enabled,
        onClick = {
            open = true
            onOpen()
        },
    )
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
        submitEnabled = languageEntryUsable(typed),
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

/** What a language row reads: the languages chosen, "All languages", or for a user left on the server's, its filter. */
@Composable
private fun languageDetail(
    value: String,
    chosen: List<String>,
    serverDefault: String?,
): String {
    val all = stringResource(R.string.server_settings_all_languages)
    val names = { codes: List<String> -> codes.joinToString(", ") { languageName(it) } }
    val onDefault = serverDefault != null && value.trim().let { it.isEmpty() || it == SERVER_LANGUAGES }
    return when {
        onDefault ->
            stringResource(
                R.string.settings_value_server_default,
                serverDefault
                    .orEmpty()
                    .languageCodes()
                    .ifEmpty { null }
                    ?.let(names) ?: all,
            )
        chosen.isEmpty() -> all
        else -> names(chosen)
    }
}

/** Languages people most often filter Discover to, after the device's own. */
private val POPULAR_LANGUAGES = listOf("en", "es", "fr", "de", "ja", "ko", "hi", "pt", "it", "zh")

/** How many languages the picker suggests. */
private const val SUGGESTED_LANGUAGES = 6

/** The languages a picker suggests: the device's own, in its order of preference, then popular ones, up to a handful. */
private fun suggestedLanguages(): List<String> {
    val locales = LocaleListCompat.getAdjustedDefault()
    val device =
        (0 until locales.size()).mapNotNull { index ->
            // The tag, not Locale.language: that still answers the legacy iw/in/ji where TMDB lists he/id/yi.
            locales[index]?.toLanguageTag()?.substringBefore('-')?.takeIf { it.isNotBlank() && it != "und" }
        }
    return (device + POPULAR_LANGUAGES).distinct().take(SUGGESTED_LANGUAGES)
}
