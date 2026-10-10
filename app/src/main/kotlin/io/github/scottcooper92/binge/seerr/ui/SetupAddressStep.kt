package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.CheckboxRow
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import com.binge.designsystem.R as DesR

/**
 * Step one: the address alone. The server is read before any credential is asked for, and an
 * address in plain HTTP to a public host is not read at all until the user ticks the opt-in under it.
 * [allow] is set while the address waits on the local-network permission: asking for it is then the way on.
 */
@Composable
internal fun SetupAddressContent(
    state: SetupUiState.Address,
    allow: AllowLocalNetwork?,
    actions: SetupActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        // Done submits, as Continue does: the address is the step's only field.
        EditorTextField(
            state.serverUrl,
            stringResource(R.string.setup_server_url),
            placeholder = stringResource(R.string.placeholder_server_url),
            enabled = !state.isInspecting,
            keyboardType = KeyboardType.Uri,
            autoCorrect = false,
            imeAction = ImeAction.Done,
            onDone = {
                if (allow != null) {
                    allow.run()
                } else if (state.canContinue) {
                    actions.onInspect()
                }
            },
            onValueChange = actions.onEditAddress,
        )
        if (state.insecure) {
            Text(
                stringResource(R.string.setup_insecure_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            CheckboxRow(
                label = stringResource(R.string.setup_allow_cleartext),
                checked = state.cleartextAllowed,
                onToggle = actions.onAllowCleartext,
                enabled = !state.isInspecting,
            )
        }
        state.error?.let { error ->
            Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (allow != null) {
            // Said before the system prompt, so a permission dialog is never the first the user hears of it.
            Text(stringResource(R.string.setup_local_network_explanation), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The address step's one commit: Continue, or the local-network ask while that is the only way on (#1099). */
@Composable
internal fun SetupAddressFooter(
    state: SetupUiState.Address,
    allow: AllowLocalNetwork?,
    onInspect: () -> Unit,
) {
    BingeFilledButton(
        label = stringResource(allow?.label ?: R.string.setup_continue),
        onClick = allow?.run ?: onInspect,
        enabled = allow != null || state.canContinue,
        loading = state.isInspecting,
        modifier = Modifier.fillMaxWidth(),
    )
}
