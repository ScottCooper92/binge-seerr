package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.InfoRow
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import com.binge.designsystem.R as DesR

/**
 * The one screen: the saved connection, or the form to make one. Built from the shared design
 * system's components and tokens, so it reads as part of Binge rather than a second app.
 */
@Composable
fun SetupScreen(
    state: SetupUiState,
    onEdit: (SetupForm.() -> SetupForm) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Scaffold(topBar = { BingeTopBar(title = stringResource(R.string.companion_name)) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                SetupUiState.Loading -> BingeLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                is SetupUiState.Disconnected -> SetupForm(state, onEdit, onConnect)
                is SetupUiState.Connected -> ConnectedPanel(state.credentials, state.isDisconnecting, onDisconnect)
            }
        }
    }
}

@Composable
private fun SetupForm(
    state: SetupUiState.Disconnected,
    onEdit: (SetupForm.() -> SetupForm) -> Unit,
    onConnect: () -> Unit,
) {
    val form = state.form
    val spacing = dimensionResource(DesR.dimen.padding_m)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = form.serverUrl,
            onValueChange = { value -> onEdit { copy(serverUrl = value) } },
            label = { Text(stringResource(R.string.setup_server_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.insecure) {
            Text(
                stringResource(R.string.setup_insecure_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            AuthMode.entries.forEach { mode ->
                FilterChip(
                    selected = form.mode == mode,
                    onClick = { onEdit { copy(mode = mode) } },
                    label = { Text(stringResource(mode.labelRes())) },
                )
            }
        }
        when (form.mode) {
            AuthMode.ApiKey ->
                OutlinedTextField(
                    value = form.apiKey,
                    onValueChange = { value -> onEdit { copy(apiKey = value) } },
                    label = { Text(stringResource(R.string.setup_api_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            AuthMode.Jellyfin, AuthMode.Local -> {
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { value -> onEdit { copy(username = value) } },
                    label = { Text(stringResource(if (form.mode == AuthMode.Local) R.string.setup_email else R.string.setup_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { value -> onEdit { copy(password = value) } },
                    label = { Text(stringResource(R.string.setup_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        state.error?.let { error ->
            Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        BingeFilledButton(
            label = stringResource(R.string.setup_connect),
            onClick = onConnect,
            enabled = form.canSubmit && !state.isConnecting,
            loading = state.isConnecting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConnectedPanel(
    credentials: SeerrCredentials,
    isDisconnecting: Boolean,
    onDisconnect: () -> Unit,
) {
    val spacing = dimensionResource(DesR.dimen.padding_m)
    Column(modifier = Modifier.fillMaxSize().padding(spacing), verticalArrangement = Arrangement.spacedBy(spacing)) {
        Text(stringResource(R.string.connected_title, credentials.variant.displayName), style = MaterialTheme.typography.titleMedium)
        InfoRow(label = stringResource(R.string.connected_server_label), value = credentials.baseUrl)
        InfoRow(
            label = stringResource(R.string.connected_access_label),
            value =
                stringResource(
                    when (credentials.auth) {
                        is SeerrAuth.ApiKey -> R.string.connected_as_api_key
                        is SeerrAuth.Session -> R.string.connected_as_user
                    },
                ),
        )
        Text(stringResource(R.string.connected_hint), style = MaterialTheme.typography.bodyMedium)
        BingeOutlinedButton(
            label = stringResource(R.string.connected_disconnect),
            onClick = onDisconnect,
            enabled = !isDisconnecting,
            loading = isDisconnecting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun AuthMode.labelRes(): Int =
    when (this) {
        AuthMode.ApiKey -> R.string.setup_mode_api_key
        AuthMode.Jellyfin -> R.string.setup_mode_jellyfin
        AuthMode.Local -> R.string.setup_mode_local
    }

private fun SetupError.messageRes(): Int =
    when (this) {
        SetupError.InvalidUrl -> R.string.setup_error_invalid_url
        SetupError.Rejected -> R.string.setup_error_rejected
        SetupError.Unreachable -> R.string.setup_error_unreachable
        SetupError.Unknown -> R.string.setup_error_unknown
    }
