package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.handoff.AddressCandidate
import io.github.scottcooper92.binge.seerr.handoff.AddressSource
import com.binge.designsystem.R as DesR

/**
 * The addresses as radio rows, best first and the chosen one marked, then "use another address" and,
 * when that is chosen, the field to type it in.
 */
@Composable
internal fun AddressChoices(
    state: SendAddressUiState.Ready,
    onChoose: (AddressChoice) -> Unit,
    onEditOther: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        state.candidates.forEach { candidate ->
            val choice = AddressChoice.Candidate(candidate.address)
            ChoiceRow(selected = state.choice == choice, enabled = !state.isSending, onClick = { onChoose(choice) }) {
                CandidateText(candidate)
            }
        }
        ChoiceRow(
            selected = state.choice == AddressChoice.Other,
            enabled = !state.isSending,
            onClick = { onChoose(AddressChoice.Other) },
        ) {
            Text(stringResource(R.string.send_address_other), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (state.choice == AddressChoice.Other) {
        OtherAddressField(state.otherAddress, state.otherInvalid, enabled = !state.isSending, onEdit = onEditOther)
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) { content() }
    }
}

/** The address as it will be sent, in monospace, with where it came from and, if it applies, why the TV may not reach it. */
@Composable
private fun CandidateText(candidate: AddressCandidate) {
    Text(candidate.address, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    Text(
        stringResource(candidate.source.labelRes()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (candidate.mayBeUnreachable) NotLocalNote()
}

/**
 * "Not a local address", and what that means for the TV. Neutral rather than an error colour: a
 * public address can be the right one, and the user may know the TV reaches it.
 */
@Composable
internal fun NotLocalNote(
    modifier: Modifier = Modifier,
    centred: Boolean = false,
) {
    val align = if (centred) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier,
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            stringResource(R.string.send_address_not_local),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = align,
        )
        Text(
            stringResource(R.string.send_address_not_local_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
        )
    }
}

/**
 * The typed address. It starts with the connected address's scheme and port and the cursor between
 * them, so the user types only the host. The TV's own rules decide whether it is an address at all.
 */
@Composable
private fun OtherAddressField(
    value: String,
    invalid: Boolean,
    enabled: Boolean,
    onEdit: (String) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.prefillCursor()))) }
    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onEdit(it.text)
        },
        label = { Text(stringResource(R.string.handoff_page_field)) },
        singleLine = true,
        enabled = enabled,
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.handoff_page_invalid)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Just after the scheme, where the host goes; the end for anything without one. */
internal fun String.prefillCursor(): Int = indexOf("://").takeIf { it >= 0 }?.plus("://".length) ?: length

internal fun AddressSource.labelRes(): Int =
    when (this) {
        AddressSource.Remembered -> R.string.send_address_source_remembered
        AddressSource.ApplicationUrl -> R.string.send_address_source_application_url
        AddressSource.Connected -> R.string.send_address_source_connected
    }
