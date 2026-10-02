package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.github.scottcooper92.binge.seerr.R

/**
 * [autoCorrect] is off for a value another system has to match exactly, such as a username.
 *
 * [prose] opts a free-text field (a name, a title) into starting each sentence capitalised. It is off by
 * default, because a locale, a path or an identifier is case-sensitive and a keyboard would otherwise
 * change what the user typed.
 *
 * [contentType] is null by default because most of these fields hold a server's secret rather than
 * the user's own credential, and a credential provider should only be offered the latter.
 *
 * A [secret] field carries its own reveal toggle, so a long pasted key can be checked before it is
 * saved. It is not optional: a masked field with no way out of it is the thing being fixed.
 *
 * Any other editable field shows a clear button while it is focused and non-empty.
 *
 * [imeAction] is Next, which moves focus on, so the last field of a form passes Done. Done drops the
 * keyboard and then runs [onDone], where submitting is the sensible next step. A multi-line field
 * keeps the keyboard's Enter, whatever [imeAction] says.
 */
@Composable
internal fun EditorTextField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secret: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    autoCorrect: Boolean = true,
    prose: Boolean = false,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    contentType: ContentType? = null,
    icon: ImageVector? = null,
    readOnly: Boolean = false,
    /** With [onToggleReveal], a masked field's reveal state is the caller's rather than the field's own. */
    revealed: Boolean? = null,
    onToggleReveal: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // remember rather than rememberSaveable: a field left revealed comes back masked after the app
    // is backgrounded, which is a small leak closed for no loss.
    var localRevealed by remember { mutableStateOf(false) }
    val shown = revealed ?: localRevealed
    val trailing: (@Composable () -> Unit)? =
        when {
            secret -> {
                { RevealToggle(revealed = shown, enabled = enabled) { onToggleReveal?.invoke() ?: run { localRevealed = !localRevealed } } }
            }
            focused && enabled && !readOnly && value.isNotEmpty() -> {
                { ClearButton { onValueChange("") } }
            }
            else -> null
        }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        leadingIcon =
            icon?.let {
                {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = rowLabelColor(enabled, MaterialTheme.colorScheme.primary),
                    )
                }
            },
        trailingIcon = trailing,
        visualTransformation = if (secret && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = editorKeyboardOptions(secret, keyboardType, autoCorrect, prose, editorImeAction(imeAction, singleLine)),
        keyboardActions =
            KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                onDone = {
                    focusManager.clearFocus()
                    onDone?.invoke()
                },
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .then(contentType?.let { type -> Modifier.semantics { this.contentType = type } } ?: Modifier),
    )
}

internal fun imeActionIf(last: Boolean): ImeAction = if (last) ImeAction.Done else ImeAction.Next

internal fun editorImeAction(
    requested: ImeAction,
    singleLine: Boolean,
): ImeAction = if (singleLine) requested else ImeAction.Default

/**
 * The X in a focused, non-empty field's trailing slot. Out of the focus order, as the eye is, so
 * the keyboard's Next lands on the next field rather than on the button beside this one.
 */
@Composable
private fun ClearButton(onClear: () -> Unit) {
    IconButton(onClick = onClear, modifier = Modifier.focusProperties { canFocus = false }) {
        Icon(imageVector = Icons.Filled.Clear, contentDescription = stringResource(R.string.field_clear))
    }
}

/**
 * The keyboard an editor field asks for. Only a field the caller marks as [prose] (a name, a title, a
 * message) starts each sentence capitalised. Everything else, whether a URL, an email, a number, a
 * secret or an identifier, is left exactly as typed, and a non-text keyboard never capitalises even
 * when asked.
 */
internal fun editorKeyboardOptions(
    secret: Boolean,
    keyboardType: KeyboardType,
    autoCorrect: Boolean,
    prose: Boolean = false,
    imeAction: ImeAction = ImeAction.Unspecified,
): KeyboardOptions {
    val type = if (secret) KeyboardType.Password else keyboardType
    return KeyboardOptions(
        capitalization = if (prose && type == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        autoCorrectEnabled = autoCorrect,
        keyboardType = type,
        imeAction = imeAction,
    )
}

/**
 * The eye in a masked field's trailing slot. Its description names what the tap will do rather than
 * what the field holds, so a screen reader announces the action and the label is not read twice.
 *
 * It follows the field's own [enabled], which is off only while a save is in flight.
 */
@Composable
private fun RevealToggle(
    revealed: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle, enabled = enabled, modifier = Modifier.focusProperties { canFocus = false }) {
        Icon(
            imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = stringResource(if (revealed) R.string.field_secret_hide else R.string.field_secret_show),
        )
    }
}
