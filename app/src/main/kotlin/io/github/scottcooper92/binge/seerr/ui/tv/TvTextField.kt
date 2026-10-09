package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.focus.tvFocusIndicator
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * A text field for a remote: the label above, a field the D-pad can land on, and the platform's own
 * keyboard on the centre button — tv-material ships no text field, so this is the foundation one dressed
 * in the TV theme. Focus is the amber ring every focusable surface here wears; the field itself never
 * changes fill, so the text stays legible while it is being typed.
 *
 * [initiallyFocused] seeds the ring for a preview and production passes false: a static frame runs no focus
 * search, so a focused frame is only renderable if focus is a parameter. [arrival] makes this the field
 * the page lands on. [fillWidth] takes the width its parent offers, for a field that shares a row, rather than
 * the form's fixed one. [autoCorrect] is off for a value the keyboard must not rewrite, such as a username.
 * [placeholder] is an example of what to type, shown only while the field is empty. [contentType]
 * offers the field to an autofill service; the panel has no Credential Manager picker, so that is the
 * whole of the hand-off here.
 */
@Composable
internal fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    autoCorrect: Boolean = true,
    contentType: ContentType? = null,
    initiallyFocused: Boolean = false,
    arrival: TvArrivalFocus? = null,
    fillWidth: Boolean = false,
    /** What the keyboard's Done (its tick, or Enter) does: the form's way on, so the remote never has to leave the field to reach it. */
    onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val focusManager = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    val input = remember { FocusRequester() }
    val frame = remember { FocusRequester() }
    LaunchedEffect(editing) { if (editing) input.requestFocus() }
    val shape = BingeShapes.TvListItem
    Column(
        modifier = if (fillWidth) modifier else modifier.width(dimensionResource(R.dimen.tv_form_field_width)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The frame is what the remote lands on; the text input inside it only takes focus once select is pressed. On a
        // TV the system raises the keyboard whenever a text input is focused, so walking down a form with the remote
        // would throw it up at every field, and the next press would type into it.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.tv_form_field_height))
                    .tvFocusIndicator(isFocused = focused || editing, shape = shape)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(dimensionResource(R.dimen.tv_form_field_border_width), MaterialTheme.colorScheme.border, shape)
                    .then(arrival?.let { Modifier.tvArrivalTarget(it) } ?: Modifier)
                    .focusRequester(frame)
                    .onFocusChanged { focused = it.isFocused }
                    .editOnSelect(enabled = enabled && !editing) { editing = true }
                    .focusable(enabled = enabled)
                    // The label is a sibling, so the field names itself for a screen reader and a test.
                    .semantics { contentDescription = label },
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = keyboardType,
                        autoCorrectEnabled = autoCorrect,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            // Back to the frame first: an input that gives up focus with nowhere to go sends it to the page's
                            // first stop, and on the sign-in form that is a tab, which would switch the mode under the user.
                            frame.requestFocus()
                            editing = false
                            onDone?.invoke()
                        },
                    ),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusRequester(input)
                        .focusProperties { canFocus = editing }
                        // The editable node names itself too: while editing, a screen reader is on this node and not the frame,
                        // and would read the value as "edit box" with no label.
                        .semantics { contentDescription = label }
                        .then(contentType?.let { type -> Modifier.semantics { this.contentType = type } } ?: Modifier)
                        // Leaving the input, by Back past the keyboard or a move, ends editing: the frame takes over again.
                        .onFocusChanged { if (!it.isFocused && editing) editing = false }
                        .leavesVertically(focusManager),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = dimensionResource(R.dimen.tv_form_field_padding_horizontal)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        // Behind the field rather than instead of it, so the cursor still sits where typing starts.
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** Select (the remote's OK, or Enter) on the frame starts editing; the press is the frame's, so it types nothing. */
private fun Modifier.editOnSelect(
    enabled: Boolean,
    onEdit: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        val select = enabled && event.isSelect()
        if (select && event.type == KeyEventType.KeyUp) onEdit()
        select
    }

private fun KeyEvent.isSelect(): Boolean = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/**
 * The input would otherwise swallow ↑ and ↓ as cursor moves it cannot make on one line, and the remote could never
 * leave it. ← and → stay the input's: they move within the typed text.
 */
private fun Modifier.leavesVertically(focusManager: FocusManager): Modifier =
    onPreviewKeyEvent { event ->
        when {
            event.type != KeyEventType.KeyDown -> false
            event.key == Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            event.key == Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            else -> false
        }
    }
