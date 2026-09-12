package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeFilledButton
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/** Step one: the address alone. The server is read before any credential is asked for. */
@Composable
internal fun SetupAddressStep(
    state: SetupUiState.Address,
    onEditAddress: (String) -> Unit,
    onInspect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = onEditAddress,
            label = { Text(stringResource(R.string.setup_server_url)) },
            singleLine = true,
            enabled = !state.isInspecting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.insecure) {
            Text(
                stringResource(R.string.setup_insecure_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.error?.let { error ->
            Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        BingeFilledButton(
            label = stringResource(R.string.setup_continue),
            onClick = onInspect,
            enabled = state.serverUrl.isNotBlank() && !state.isInspecting,
            loading = state.isInspecting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
