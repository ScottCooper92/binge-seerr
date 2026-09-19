package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupServer
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.SignInForm
import io.github.scottcooper92.binge.seerr.ui.label
import io.github.scottcooper92.binge.seerr.ui.messageRes
import io.github.scottcooper92.binge.seerr.ui.submitLabelRes

/** The control a preview seeds as focused; production passes null and the page lands where it lands. */
internal enum class TvSetupFocus { Address, Continue, Credential, Connect }

/**
 * The two steps to a connection, on a television: the address, then the sign-ins a remote can finish.
 * The same ViewModel and the same [SetupUiState] as the phone's setup. Quick Connect and Plex both
 * finish elsewhere rather than typed — a code approved in another Jellyfin app, or typed at
 * plex.tv/link — so the television only has to show the code and wait.
 */
@Composable
internal fun TvSetupScreen(
    state: SetupUiState,
    actions: SetupActions,
    modifier: Modifier = Modifier,
    initialFocus: TvSetupFocus? = null,
) {
    when (state) {
        // The home swaps to the connected plate on the credentials landing; this is the frame in between.
        SetupUiState.Loading, is SetupUiState.Connected -> TvLoadingPlate(modifier = modifier)
        is SetupUiState.Address -> TvSetupAddressStep(state, actions, modifier, initialFocus)
        // A link flow takes the whole page: the code is the only thing to read, and the only thing to do
        // is wait or back out.
        is SetupUiState.SignIn ->
            state.link
                ?.let { link -> TvSetupLinkPlate(link, actions.onCancelLink, modifier) }
                ?: TvSetupSignInStep(state, actions, modifier, initialFocus)
    }
}

@Composable
private fun TvSetupAddressStep(
    state: SetupUiState.Address,
    actions: SetupActions,
    modifier: Modifier,
    initialFocus: TvSetupFocus?,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvFormPage(
        headline = stringResource(R.string.tv_setup_headline),
        body = stringResource(R.string.tv_setup_address_body),
        icon = Icons.Filled.Dns,
        modifier = modifier,
    ) {
        TvTextField(
            value = state.serverUrl,
            onValueChange = actions.onEditAddress,
            label = stringResource(R.string.setup_server_url),
            enabled = !state.isInspecting,
            keyboardType = KeyboardType.Uri,
            placeholder = stringResource(R.string.placeholder_server_url),
            initiallyFocused = initialFocus == TvSetupFocus.Address,
            arrival = arrival,
        )
        if (state.insecure) {
            TvFormNote(stringResource(R.string.setup_insecure_warning), tone = TvFormNoteTone.Error)
        }
        state.error?.let { error -> TvFormNote(stringResource(error.messageRes()), tone = TvFormNoteTone.Error) }
        TvButton(
            label = stringResource(if (state.isInspecting) R.string.tv_setup_checking else R.string.setup_continue),
            onClick = actions.onInspect,
            style = TvButtonStyle.Primary,
            enabled = state.serverUrl.isNotBlank() && !state.isInspecting,
            initiallyFocused = initialFocus == TvSetupFocus.Continue,
        )
    }
}

/**
 * The sign-ins a remote can finish — which, now, is all of them. A key or an account typed on
 * screen; Quick Connect, whose code is approved in any Jellyfin app the user is already signed
 * into; and Plex, whose code is typed at plex.tv/link on another device rather than opened in a
 * browser this surface does not have. The television only has to show a code and wait either way.
 */
internal val SeerrSignInMode.finishableOnTv: Boolean
    get() =
        when (this) {
            SeerrSignInMode.ApiKey, SeerrSignInMode.Local, SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> true
            SeerrSignInMode.QuickConnect, SeerrSignInMode.Plex -> true
        }

@Composable
private fun TvSetupSignInStep(
    state: SetupUiState.SignIn,
    actions: SetupActions,
    modifier: Modifier,
    initialFocus: TvSetupFocus?,
) {
    val offered = state.server.modes.filter { it.finishableOnTv }
    // The form opens on the server's first mode, which may be one this surface cannot finish.
    val modeOffered = state.form.mode in offered
    LaunchedEffect(modeOffered, offered) {
        if (!modeOffered && offered.isNotEmpty()) actions.onEditForm { copy(mode = offered.first()) }
    }
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvFormPage(
        headline = state.server.title,
        body = state.server.editionLine(),
        note = stringResource(R.string.tv_setup_sign_in_body),
        icon = Icons.Filled.Lock,
        modifier = modifier,
    ) {
        if (offered.isEmpty()) {
            TvFormNote(stringResource(R.string.tv_setup_no_modes_here))
            TvButton(
                label = stringResource(R.string.setup_change_server),
                onClick = actions.onChangeServer,
                style = TvButtonStyle.Primary,
                modifier = Modifier.tvArrivalTarget(arrival),
            )
        } else {
            TvOptionGroup(
                title = stringResource(R.string.tv_setup_mode_title),
                choices = offered.map { mode -> mode to mode.label(state.server) },
                selected = state.form.mode,
                onSelect = { mode -> actions.onEditForm { copy(mode = mode) } },
                arrival = arrival,
            )
            TvModeFields(state.form, state.server, actions.onEditForm, initialFocus == TvSetupFocus.Credential)
            state.error?.let { error -> TvFormNote(stringResource(error.messageRes()), tone = TvFormNoteTone.Error) }
            state.notice?.let { notice -> TvFormNote(stringResource(notice.messageRes()), tone = TvFormNoteTone.Success) }
            TvButton(
                label = stringResource(if (state.isConnecting) R.string.tv_setup_connecting else state.form.mode.submitLabelRes()),
                onClick = actions.onConnect,
                style = TvButtonStyle.Primary,
                enabled = state.form.canSubmit && !state.isConnecting && state.link == null,
                initiallyFocused = initialFocus == TvSetupFocus.Connect,
            )
            TvButton(label = stringResource(R.string.setup_change_server), onClick = actions.onChangeServer)
        }
    }
}

@Composable
private fun SetupServer.editionLine(): String =
    versionLabel
        ?.let { stringResource(R.string.setup_server_edition, variant.displayName, it) }
        ?: stringResource(R.string.setup_server_development, variant.displayName)

/** The fields the chosen mode needs, and only those: a key, or an identity and a password. */
@Composable
private fun TvModeFields(
    form: SignInForm,
    server: SetupServer,
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
    credentialFocused: Boolean,
) {
    when (form.mode) {
        SeerrSignInMode.ApiKey -> {
            TvTextField(
                value = form.apiKey,
                onValueChange = { value -> onEdit { copy(apiKey = value) } },
                label = stringResource(R.string.setup_api_key),
                secret = true,
                keyboardType = KeyboardType.Password,
                // No content type: the key is the server's, not an account credential.
                initiallyFocused = credentialFocused,
            )
            // TvTextField has no supporting slot, and this has to stay readable while the user
            // fetches the key — so it is the note the page already uses for what it wants said.
            TvFormNote(stringResource(R.string.setup_api_key_hint))
        }
        SeerrSignInMode.Local -> {
            TvTextField(
                value = form.email,
                onValueChange = { value -> onEdit { copy(email = value) } },
                label = stringResource(R.string.setup_email),
                placeholder = stringResource(R.string.placeholder_email),
                keyboardType = KeyboardType.Email,
                // Both types: the local account is an email address and it is also the username the
                // saved login is filed under.
                contentType = ContentType.EmailAddress + ContentType.Username,
                initiallyFocused = credentialFocused,
            )
            TvPasswordField(form, onEdit)
        }
        SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> {
            TvTextField(
                value = form.username,
                onValueChange = { value -> onEdit { copy(username = value) } },
                label = stringResource(R.string.setup_username),
                placeholder = stringResource(R.string.setup_username_placeholder, form.mode.label(server)),
                autoCorrect = false,
                contentType = ContentType.Username,
                initiallyFocused = credentialFocused,
            )
            TvPasswordField(form, onEdit)
        }
        // Neither needs typed fields: pressing Connect below mints its code, and the plate above takes over.
        SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect -> Unit
    }
}

@Composable
private fun TvPasswordField(
    form: SignInForm,
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
) {
    TvTextField(
        value = form.password,
        onValueChange = { value -> onEdit { copy(password = value) } },
        label = stringResource(R.string.setup_password),
        secret = true,
        keyboardType = KeyboardType.Password,
        contentType = ContentType.Password,
    )
}
