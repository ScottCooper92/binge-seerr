package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.CheckboxRow
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemConnector
import com.binge.designsystem.component.TextEntrySurface
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.PeekingListSheet
import com.binge.designsystem.R as DesR

/** The keyboard a number asks for: a port, a count, a number of seconds. */
internal val NumberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

/** The keyboard a number with a fractional part asks for, such as a timeout in seconds. */
internal val DecimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/** The keyboard an email address asks for, with no autocorrect to rewrite it. */
internal val EmailKeyboard = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false)

/** Rows that hang beneath the switch above them, joined to it by the design system's connector. */
internal fun List<ListItem>.joined(): List<ListItem> =
    mapIndexed { index, row -> row.copy(connector = if (index == lastIndex) ListItemConnector.End else ListItemConnector.Continue) }

/** The keyboard an address asks for: a host, a URL or a URL base, with no autocorrect to rewrite it. */
internal val AddressKeyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false)

/** The keyboard a name the server matches exactly asks for, such as a username: text, with no autocorrect. */
internal val VerbatimKeyboard = KeyboardOptions(autoCorrectEnabled = false)

/** How tall a [textSettingItem] sheet's field opens for a value of several lines, and how far it grows. */
private const val MULTILINE_MIN = 3
private const val MULTILINE_MAX = 8

/**
 * A text setting as a list row for an `ItemGroup`: its name, and its value (or [emptyLabel] when blank) as the detail;
 * [shown] replaces that for a value the row shouldn't spell out, such as a key.
 * A tap edits it in the design system's text entry sheet; [check] names what is wrong with a value, which keeps Done
 * off. A [secret] value is edited in a masked field with a reveal toggle and no autocorrect, as an `EditorTextField`
 * does, since the design system's text entry has no way to hide what it shows. A [hint] explains the value and stays
 * in view while it is edited; a [placeholder] is an example of one, shown only while the field is empty. A [required]
 * row left blank, or a value [check] refuses, says so in its detail, in the error colour. [keyboard] is the keyboard the
 * sheet asks for: [NumberKeyboard], [AddressKeyboard] or [VerbatimKeyboard] where the value needs one.
 * The sheet belongs to this call, so a page lists its rows and nothing else.
 */
@Composable
internal fun textSettingItem(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    emptyLabel: String = stringResource(R.string.settings_value_not_set),
    hint: String? = null,
    placeholder: String? = null,
    required: Boolean = false,
    check: (String) -> String? = { null },
    shown: String = value.ifBlank { emptyLabel },
    secret: Boolean = false,
    multiline: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(value) }
        // A secret is kept as typed: a password's edge spaces are part of it. A caller that wants a key trimmed trims it.
        val submitted = if (secret) draft else draft.trim()
        val problem = check(submitted)
        BingeBottomSheet(onDismissRequest = { open = false }) {
            if (secret) {
                SecretEntry(
                    title = label,
                    value = draft,
                    hint = hint,
                    onValueChange = { draft = it },
                    submitEnabled = problem == null,
                    onSubmit = {
                        onChange(submitted)
                        open = false
                    },
                    onCancel = { open = false },
                )
            } else {
                TextEntrySurface(
                    title = label,
                    value = draft,
                    onValueChange = { draft = it },
                    onSubmit = {
                        onChange(submitted)
                        open = false
                    },
                    onCancel = { open = false },
                    submitLabel = stringResource(R.string.editor_done),
                    // The surface's own hint is a placeholder, gone once the field holds a value; an explanation
                    // has to stay readable while the value is edited, so it sits under the title instead.
                    hint = placeholder,
                    header = { hint?.let { SheetHint(it) } },
                    submitEnabled = problem == null,
                    error = problem.takeIf { draft.isNotEmpty() },
                    minLines = if (multiline) MULTILINE_MIN else 1,
                    maxLines = if (multiline) MULTILINE_MAX else 1,
                    keyboardOptions = keyboard,
                    modifier = Modifier.imePadding(),
                )
            }
        }
    }
    val rowProblem = rowProblem(value, required, secret, check)
    return ListItem(
        icon = icon,
        label = label,
        detail = rowProblem ?: shown,
        detailColor = if (rowProblem != null) MaterialTheme.colorScheme.error else null,
        clickable = enabled,
        disabled = !enabled,
        onClick = { open = true },
    )
}

/**
 * What a text row says in place of its value when it is wrong: a required value left blank, or one the server sent that
 * the row's own check refuses. On a page with a Save, Save follows the form's validity, so this is where the user looks
 * for why. A page that saves as it changes sends a value the server sent back as it came, so the row only says so; its
 * sheets refuse to make such a value, so a change of the user's never leaves one behind.
 */
@Composable
private fun rowProblem(
    value: String,
    required: Boolean,
    secret: Boolean,
    check: (String) -> String?,
): String? =
    when {
        required && value.isBlank() -> stringResource(R.string.editor_field_required)
        value.isNotBlank() -> check(if (secret) value else value.trim())
        else -> null
    }

/**
 * A line under an `ItemGroup`'s rows, through its `belowRows`: what the group still needs before Save. [error] marks
 * something wrong in what was entered; a step not yet taken, such as a new rule's first condition, is not an error.
 */
@Composable
internal fun GroupMessage(
    text: String,
    error: Boolean,
) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
        Modifier.padding(
            horizontal = dimensionResource(DesR.dimen.padding_m),
            vertical = dimensionResource(DesR.dimen.padding_s),
        ),
)

/** [textSettingItem]'s sheet for a secret: the title, a masked [EditorTextField], and the same Cancel and Done pair. */
@Composable
private fun SecretEntry(
    title: String,
    value: String,
    hint: String?,
    onValueChange: (String) -> Unit,
    submitEnabled: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().imePadding().padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        EditorTextField(
            value = value,
            label = title,
            secret = true,
            autoCorrect = false,
            supporting = hint,
            imeAction = ImeAction.Done,
            onDone = { if (submitEnabled) onSubmit() },
            onValueChange = onValueChange,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            BingeOutlinedButton(label = stringResource(R.string.editor_cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            BingeFilledButton(
                label = stringResource(R.string.editor_done),
                onClick = onSubmit,
                enabled = submitEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** An explanation under a text sheet's title, readable while the field holds a value. */
@Composable
private fun SheetHint(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/**
 * A pick from a fixed list as a list row: the setting's name, and what it is set to now. A tap opens the shared
 * choice sheet, each choice with [choiceIcon]'s icon or else the setting's; picking one applies it and closes. The sheet closes if a save
 * starts, so a pick can't land in a draft that has already gone out.
 */
@Composable
internal fun <T> choiceSettingItem(
    icon: ImageVector,
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    enabled: Boolean,
    choiceIcon: (T) -> ImageVector? = { null },
    onSelect: (T) -> Unit,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open) {
        BingeChoiceSheet(
            title = title,
            // Every choice carries an icon: its own where it has one, else the setting's.
            choices = BingeChoiceList.Ready(choices.map { (value, label) -> BingeChoice(value, label, icon = choiceIcon(value) ?: icon) }),
            selected = selected,
            onSelect = onSelect,
            onDismiss = { open = false },
        )
    }
    return ListItem(
        icon = icon,
        label = title,
        detail =
            choices.firstOrNull { it.first == selected }?.second
                ?: stringResource(if (selected == null) R.string.settings_value_not_set else R.string.settings_value_unknown),
        clickable = enabled,
        disabled = !enabled,
        onClick = { open = true },
    )
}

/**
 * A pick of any number from a list, as a list row: the setting's name, and the picks named (or [emptyLabel]). A tap
 * opens a peeking checklist; each tick applies at once through [onToggle], so closing the sheet is all that's left.
 */
@Composable
internal fun <T> multiChoiceSettingItem(
    icon: ImageVector,
    title: String,
    choices: List<Pair<T, String>>,
    selected: Set<T>,
    enabled: Boolean,
    emptyLabel: String,
    onToggle: (T) -> Unit,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open) {
        PeekingListSheet(title = title, onDismiss = { open = false }) {
            choices.forEachIndexed { index, (choice, label) ->
                CheckboxRow(
                    label = label,
                    checked = choice in selected,
                    onToggle = { onToggle(choice) },
                    showDivider = index < choices.lastIndex,
                )
            }
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_l)))
        }
    }
    return ListItem(
        icon = icon,
        label = title,
        detail = choices.filter { it.first in selected }.joinToString(", ") { it.second }.ifEmpty { emptyLabel },
        clickable = enabled,
        disabled = !enabled,
        onClick = { open = true },
    )
}
