package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.contentDescription
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
 * the page lands on.
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
    initiallyFocused: Boolean = false,
    arrival: TvArrivalFocus? = null,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val focusManager = LocalFocusManager.current
    val shape = BingeShapes.TvListItem
    Column(
        modifier = modifier.width(dimensionResource(R.dimen.tv_form_field_width)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.tv_form_field_height))
                    .tvFocusIndicator(isFocused = focused, shape = shape)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(dimensionResource(R.dimen.tv_form_field_border_width), MaterialTheme.colorScheme.border, shape)
                    .then(arrival?.let { Modifier.tvArrivalTarget(it) } ?: Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    // The field would otherwise swallow ↑ and ↓ as cursor moves it cannot make on one line, and the
                    // remote could never leave it. ← and → stay the field's: they move within the typed text.
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type != KeyEventType.KeyDown -> false
                            event.key == Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                            event.key == Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                            else -> false
                        }
                    }
                    // The label is a sibling, so the field names itself for a screen reader and a test.
                    .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = dimensionResource(R.dimen.tv_form_field_padding_horizontal)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    inner()
                }
            },
        )
    }
}
