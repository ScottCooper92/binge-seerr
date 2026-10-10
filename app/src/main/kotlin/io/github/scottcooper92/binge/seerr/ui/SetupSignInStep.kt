package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import coil3.compose.AsyncImage
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.template.StepHeading
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import com.binge.designsystem.R as DesR

/** Step two's aside: the server's own artwork, where it has some. */
@Composable
internal fun SetupServerBackdrop(server: SetupServer) {
    server.backdropUrl?.let { url ->
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.setup_backdrop_height))
                    .clip(BingeShapes.Large),
        )
    }
}

/** Step two's heading: the server's name, and what it is. */
@Composable
internal fun SetupServerHeading(server: SetupServer) {
    StepHeading(
        title = server.title,
        subtitle =
            server.versionLabel
                ?.let { stringResource(R.string.setup_server_edition, server.variant.displayName, it) }
                ?: stringResource(R.string.setup_server_development, server.variant.displayName),
    )
}

/** Step two: only the sign-ins the server accepts, and the fields of the one chosen. */
@Composable
internal fun SetupSignInContent(
    state: SetupUiState.SignIn,
    actions: SetupActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        ModeChips(state.server, state.form.mode) { mode -> actions.onEditForm { copy(mode = mode) } }
        ModeFields(state, actions.onEditForm, actions.onRequestPasswordReset, actions.onConnect)
        state.error?.let { error ->
            Text(
                stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.notice?.let { notice ->
            Text(
                stringResource(notice.messageRes()),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Step two's commit, and the way off a server that rejected the session where the app offers one (#810). */
@Composable
internal fun SetupSignInFooter(
    state: SetupUiState.SignIn,
    actions: SetupActions,
) {
    BingeFilledButton(
        label = stringResource(state.form.mode.submitLabelRes()),
        onClick = actions.onConnect,
        enabled = state.form.canSubmit && !state.isConnecting && state.link == null,
        loading = state.isConnecting,
        modifier = Modifier.fillMaxWidth(),
    )
    actions.onDisconnect?.let { DisconnectButton(it) }
}

@Composable
internal fun ModeChips(
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
internal fun ModeFields(
    state: SetupUiState.SignIn,
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
    onRequestPasswordReset: () -> Unit,
    onConnect: () -> Unit,
) {
    val form = state.form
    val submit = { if (form.canSubmit && !state.isConnecting && state.link == null) onConnect() }
    when (form.mode) {
        // No content type: the key is the server's, not an account credential, and offering to save
        // it as this user's password is how a password manager ends up holding the wrong secret.
        SeerrSignInMode.ApiKey -> {
            EditorTextField(
                form.apiKey,
                stringResource(R.string.setup_api_key),
                secret = true,
                // Supporting rather than a placeholder: it has to stay readable while the user goes to fetch the key.
                supporting = stringResource(R.string.setup_api_key_hint),
                imeAction = ImeAction.Done,
                onDone = submit,
            ) { value -> onEdit { copy(apiKey = value) } }
            // An API key is the server's own, so it carries the administrator's reach to every host this
            // companion admits; the user is told before they hand it over (#684).
            Text(
                stringResource(R.string.setup_api_key_admin_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SeerrSignInMode.Local -> {
            OutlinedTextField(
                value = form.email,
                onValueChange = { value -> onEdit { copy(email = value) } },
                label = { Text(stringResource(R.string.setup_email)) },
                placeholder = { Text(stringResource(R.string.placeholder_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                // Both types: the local account is an email address and it is also the username the
                // saved login is filed under.
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.EmailAddress + ContentType.Username }
                        .savedLoginRequest { login -> onEdit { copy(email = login.id, password = login.password) } },
            )
            EditorTextField(
                form.password,
                stringResource(R.string.setup_password),
                secret = true,
                contentType = ContentType.Password,
                imeAction = ImeAction.Done,
                onDone = submit,
            ) { value -> onEdit { copy(password = value) } }
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
                placeholder = { Text(stringResource(R.string.setup_username_placeholder, form.mode.label(state.server))) },
                singleLine = true,
                // A plain text field is autocorrected, and a rewritten username fails sign-in with no visible cause.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Username }
                        .savedLoginRequest { login -> onEdit { copy(username = login.id, password = login.password) } },
            )
            EditorTextField(
                form.password,
                stringResource(R.string.setup_password),
                secret = true,
                contentType = ContentType.Password,
                imeAction = ImeAction.Done,
                onDone = submit,
            ) { value -> onEdit { copy(password = value) } }
        }
        SeerrSignInMode.Plex -> Text(stringResource(R.string.setup_plex_hint), style = MaterialTheme.typography.bodyMedium)
        SeerrSignInMode.QuickConnect -> Text(stringResource(R.string.setup_quick_connect_hint), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The Jellyfin/Emby chip carries the server's own name where the admin set one. */
@Composable
internal fun SeerrSignInMode.label(server: SetupServer): String =
    when (this) {
        SeerrSignInMode.ApiKey -> stringResource(R.string.setup_mode_api_key)
        SeerrSignInMode.Local -> stringResource(R.string.setup_mode_local)
        SeerrSignInMode.Plex -> stringResource(R.string.setup_mode_plex)
        SeerrSignInMode.Jellyfin -> server.mediaServerName ?: stringResource(R.string.setup_mode_jellyfin)
        SeerrSignInMode.Emby -> server.mediaServerName ?: stringResource(R.string.setup_mode_emby)
        SeerrSignInMode.QuickConnect -> stringResource(R.string.setup_mode_quick_connect)
    }

internal fun SeerrSignInMode.submitLabelRes(): Int =
    when (this) {
        SeerrSignInMode.Plex -> R.string.setup_sign_in_plex
        SeerrSignInMode.QuickConnect -> R.string.setup_start_quick_connect
        SeerrSignInMode.ApiKey -> R.string.setup_connect
        SeerrSignInMode.Local, SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> R.string.setup_sign_in
    }

internal fun SetupNotice.messageRes(): Int =
    when (this) {
        SetupNotice.ResetEmailSent -> R.string.setup_reset_email_sent
        SetupNotice.SessionRejected -> R.string.setup_session_rejected
    }
