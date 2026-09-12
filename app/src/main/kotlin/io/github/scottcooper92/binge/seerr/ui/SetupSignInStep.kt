package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import coil3.compose.AsyncImage
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeTextButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import com.binge.designsystem.R as DesR

/** Step two: the server's name over its artwork, then only the sign-ins it accepts. */
@Composable
internal fun SetupSignInStep(
    state: SetupUiState.SignIn,
    actions: SetupActions,
) {
    val inset = dimensionResource(DesR.dimen.screen_content_inset)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ServerHeader(state.server, actions.onChangeServer)
        Column(
            modifier = Modifier.fillMaxWidth().padding(inset),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            ModeChips(state.server, state.form.mode) { mode -> actions.onEditForm { copy(mode = mode) } }
            ModeFields(state, actions.onEditForm, actions.onRequestPasswordReset)
            state.error?.let { error ->
                Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            state.notice?.let { notice ->
                Text(stringResource(notice.messageRes()), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            BingeFilledButton(
                label = stringResource(state.form.mode.submitLabelRes()),
                onClick = actions.onConnect,
                enabled = state.form.canSubmit && !state.isConnecting && state.link == null,
                loading = state.isConnecting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ServerHeader(
    server: SetupServer,
    onChangeServer: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.setup_backdrop_height))) {
        server.backdropUrl?.let { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, surface))))
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
        ) {
            Text(server.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                server.versionLabel
                    ?.let { stringResource(R.string.setup_server_edition, server.variant.displayName, it) }
                    ?: stringResource(R.string.setup_server_development, server.variant.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BingeTextButton(label = stringResource(R.string.setup_change_server), onClick = onChangeServer)
        }
    }
}

@Composable
private fun ModeChips(
    server: SetupServer,
    selected: SeerrSignInMode,
    onSelect: (SeerrSignInMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        server.modes.forEach { mode ->
            BingeFilterChip(
                label = mode.label(server),
                selected = mode == selected,
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun ModeFields(
    state: SetupUiState.SignIn,
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
    onRequestPasswordReset: () -> Unit,
) {
    val form = state.form
    when (form.mode) {
        SeerrSignInMode.ApiKey ->
            SecretField(form.apiKey, stringResource(R.string.setup_api_key)) { value -> onEdit { copy(apiKey = value) } }
        SeerrSignInMode.Local -> {
            OutlinedTextField(
                value = form.email,
                onValueChange = { value -> onEdit { copy(email = value) } },
                label = { Text(stringResource(R.string.setup_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            SecretField(form.password, stringResource(R.string.setup_password)) { value -> onEdit { copy(password = value) } }
            if (state.server.canResetPassword) {
                BingeTextButton(
                    label = stringResource(R.string.setup_forgot_password),
                    onClick = onRequestPasswordReset,
                    enabled = form.canRequestReset && !state.isConnecting,
                )
            }
        }
        SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> {
            OutlinedTextField(
                value = form.username,
                onValueChange = { value -> onEdit { copy(username = value) } },
                label = { Text(stringResource(R.string.setup_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SecretField(form.password, stringResource(R.string.setup_password)) { value -> onEdit { copy(password = value) } }
        }
        SeerrSignInMode.Plex -> Text(stringResource(R.string.setup_plex_hint), style = MaterialTheme.typography.bodyMedium)
        SeerrSignInMode.QuickConnect -> Text(stringResource(R.string.setup_quick_connect_hint), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SecretField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The Jellyfin/Emby chip carries the server's own name where the admin set one. */
@Composable
private fun SeerrSignInMode.label(server: SetupServer): String =
    when (this) {
        SeerrSignInMode.ApiKey -> stringResource(R.string.setup_mode_api_key)
        SeerrSignInMode.Local -> stringResource(R.string.setup_mode_local)
        SeerrSignInMode.Plex -> stringResource(R.string.setup_mode_plex)
        SeerrSignInMode.Jellyfin -> server.mediaServerName ?: stringResource(R.string.setup_mode_jellyfin)
        SeerrSignInMode.Emby -> server.mediaServerName ?: stringResource(R.string.setup_mode_emby)
        SeerrSignInMode.QuickConnect -> stringResource(R.string.setup_mode_quick_connect)
    }

private fun SeerrSignInMode.submitLabelRes(): Int =
    when (this) {
        SeerrSignInMode.Plex -> R.string.setup_sign_in_plex
        SeerrSignInMode.QuickConnect -> R.string.setup_start_quick_connect
        SeerrSignInMode.ApiKey -> R.string.setup_connect
        SeerrSignInMode.Local, SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> R.string.setup_sign_in
    }

private fun SetupNotice.messageRes(): Int =
    when (this) {
        SetupNotice.ResetEmailSent -> R.string.setup_reset_email_sent
    }
