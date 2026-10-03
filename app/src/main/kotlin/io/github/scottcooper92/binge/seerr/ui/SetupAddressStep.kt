package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.binge.designsystem.component.HintCard
import com.binge.designsystem.resolvedContentInset
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import com.binge.designsystem.R as DesR

/**
 * Step one: the address alone. The server is read before any credential is asked for, and an
 * address in plain HTTP to a public host is not read at all until the user ticks the opt-in under it.
 */
@Composable
internal fun SetupAddressStep(
    state: SetupUiState.Address,
    onEditAddress: (String) -> Unit,
    onInspect: () -> Unit,
    onAllowCleartext: (Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(resolvedContentInset()),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        HintCard(text = stringResource(R.string.setup_intro))
        // Done submits, as Continue does: the address is the step's only field.
        EditorTextField(
            state.serverUrl,
            stringResource(R.string.setup_server_url),
            placeholder = stringResource(R.string.placeholder_server_url),
            enabled = !state.isInspecting,
            keyboardType = KeyboardType.Uri,
            autoCorrect = false,
            imeAction = ImeAction.Done,
            onDone = { if (state.canContinue) onInspect() },
            onValueChange = onEditAddress,
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
                onToggle = onAllowCleartext,
                enabled = !state.isInspecting,
            )
        }
        state.error?.let { error ->
            Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        BingeFilledButton(
            label = stringResource(R.string.setup_continue),
            onClick = onInspect,
            enabled = state.canContinue,
            loading = state.isInspecting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
