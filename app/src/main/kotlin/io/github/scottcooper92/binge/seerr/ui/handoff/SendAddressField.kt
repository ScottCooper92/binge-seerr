package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.handoff.AddressCandidate
import io.github.scottcooper92.binge.seerr.handoff.AddressSource
import com.binge.designsystem.R as DesR

/**
 * The address to send, always editable, with what it is told from what it holds: the TV form's
 * "not an address" as the field's error, or a neutral note that the address is not local. Under it,
 * the server's other known addresses as chips that fill the field.
 */
@Composable
internal fun SendAddressField(
    state: SendAddressUiState.Ready,
    onEdit: (String) -> Unit,
    onSend: () -> Unit,
) {
    OutlinedTextField(
        value = state.address,
        onValueChange = onEdit,
        label = { Text(stringResource(R.string.handoff_page_field)) },
        singleLine = true,
        enabled = !state.isSending,
        isError = state.isInvalid,
        supportingText = supportingText(state),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Send,
            ),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.suggestions.isNotEmpty()) Suggestions(state.suggestions, enabled = !state.isSending, onPick = onEdit)
}

private fun supportingText(state: SendAddressUiState.Ready): (@Composable () -> Unit)? =
    when {
        state.isInvalid -> ({ Text(stringResource(R.string.handoff_page_invalid)) })
        state.isNotLocal -> ({ Text(stringResource(R.string.send_address_not_local)) })
        else -> null
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(
    suggestions: List<AddressCandidate>,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        suggestions.forEach { candidate ->
            SuggestionChip(
                onClick = { onPick(candidate.address) },
                enabled = enabled,
                label = {
                    Column {
                        Text(candidate.address, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                        Text(
                            stringResource(candidate.source.tagRes()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

internal fun AddressSource.tagRes(): Int =
    when (this) {
        AddressSource.Remembered -> R.string.send_address_tag_sent_before
        AddressSource.ApplicationUrl -> R.string.send_address_tag_server_url
        AddressSource.Connected -> R.string.send_address_tag_this_phone
    }
