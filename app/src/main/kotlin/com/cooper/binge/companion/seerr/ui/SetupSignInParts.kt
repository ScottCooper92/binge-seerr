package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.binge.designsystem.component.BingeTabRow
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.BingeTextField
import com.binge.designsystem.component.heroCopyFade
import com.binge.designsystem.template.StepHeading
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode

/**
 * Step two's aside: the server's own artwork with its name and edition set into the foot of it, on the same page-colour fade a hero's copy
 * has ([heroCopyFade]). A server with no artwork has nothing to set them in, so they stand as the plain heading instead.
 */
@Composable
internal fun SetupServerBackdrop(server: SetupServer) {
    val url = server.backdropUrl
    if (url == null) {
        SetupServerHeading(server)
        return
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.setup_backdrop_height))
                .clip(BingeShapes.Large),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .heroCopyFade()
                    .padding(dimensionResource(R.dimen.padding_l)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xs)),
        ) {
            Text(
                text = server.title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = server.editionLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Step two's heading: the server's name, and what it is. */
@Composable
internal fun SetupServerHeading(server: SetupServer) {
    StepHeading(title = server.title, subtitle = server.editionLabel())
}

@Composable
private fun SetupServer.editionLabel(): String =
    versionLabel
        ?.let { stringResource(R.string.setup_server_edition, variant.displayName, it) }
        ?: stringResource(R.string.setup_server_development, variant.displayName)

/** The sign-in modes the server accepts, as tabs: each shows different fields below, so this switches content rather than narrowing a list. */
@Composable
internal fun ModeTabs(
    server: SetupServer,
    selected: SeerrSignInMode,
    onSelect: (SeerrSignInMode) -> Unit,
) {
    BingeTabRow(
        tabs = server.modes.map { it.label(server) },
        selectedIndex = server.modes.indexOf(selected),
        onSelect = { index -> onSelect(server.modes[index]) },
        // The fields below already sit inside the screen's gutter, so the row takes none of its own.
        edgePadding = dimensionResource(R.dimen.setup_mode_tabs_edge_padding),
    )
}

@Composable
internal fun ModeFields(
    state: SetupUiState.SignIn,
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
    onRequestPasswordReset: () -> Unit,
    onConnect: () -> Unit,
    mode: SeerrSignInMode = state.form.mode,
) {
    val form = state.form
    val submit = { if (form.canSubmit && !state.isConnecting && state.link == null) onConnect() }
    when (mode) {
        // No content type: the key is the server's, not an account credential, and offering to save
        // it as this user's password is how a password manager ends up holding the wrong secret.
        SeerrSignInMode.ApiKey -> {
            BingeTextField(
                value = form.apiKey,
                label = stringResource(R.string.setup_api_key),
                secret = true,
                // Supporting rather than a placeholder: it has to stay readable while the user goes to fetch the key.
                supporting = stringResource(R.string.setup_api_key_hint),
                imeAction = ImeAction.Done,
                onDone = submit,
                onValueChange = { value -> onEdit { copy(apiKey = value) } },
            )
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
            BingeTextField(
                value = form.password,
                label = stringResource(R.string.setup_password),
                secret = true,
                contentType = ContentType.Password,
                imeAction = ImeAction.Done,
                onDone = submit,
                onValueChange = { value -> onEdit { copy(password = value) } },
            )
            if (state.server.canResetPassword) {
                BingeTextButton(
                    label = stringResource(R.string.setup_forgot_password),
                    onClick = onRequestPasswordReset,
                    enabled = form.canRequestReset && !state.isConnecting && !state.resetCoolingDown,
                )
            }
        }
        SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> {
            OutlinedTextField(
                value = form.username,
                onValueChange = { value -> onEdit { copy(username = value) } },
                label = { Text(stringResource(R.string.setup_username)) },
                placeholder = { Text(stringResource(R.string.setup_username_placeholder, mode.label(state.server))) },
                singleLine = true,
                // A plain text field is autocorrected, and a rewritten username fails sign-in with no visible cause.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Username }
                        .savedLoginRequest { login -> onEdit { copy(username = login.id, password = login.password) } },
            )
            BingeTextField(
                value = form.password,
                label = stringResource(R.string.setup_password),
                secret = true,
                contentType = ContentType.Password,
                imeAction = ImeAction.Done,
                onDone = submit,
                onValueChange = { value -> onEdit { copy(password = value) } },
            )
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
