package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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
 * The two steps to a connection, on a television: the address, then the sign-ins a remote can type. The
 * same ViewModel and the same [SetupUiState] as the phone's setup, with the link flows left out — a Plex
 * or Quick Connect sign-in finishes on another device, and the phone app is that device for now.
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
        is SetupUiState.SignIn -> TvSetupSignInStep(state, actions, modifier, initialFocus)
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

/** The sign-ins a remote can finish: a key or an account typed on screen, never a code approved elsewhere. */
internal val SeerrSignInMode.typedOnTv: Boolean
    get() =
        when (this) {
            SeerrSignInMode.ApiKey, SeerrSignInMode.Local, SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> true
            SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect -> false
        }

@Composable
private fun TvSetupSignInStep(
    state: SetupUiState.SignIn,
    actions: SetupActions,
    modifier: Modifier,
    initialFocus: TvSetupFocus?,
) {
    val typed = state.server.modes.filter { it.typedOnTv }
    // The form opens on the server's first mode, which may be one this surface cannot finish.
    val modeOffered = state.form.mode in typed
    LaunchedEffect(modeOffered, typed) {
        if (!modeOffered && typed.isNotEmpty()) actions.onEditForm { copy(mode = typed.first()) }
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
        if (typed.isEmpty()) {
            TvFormNote(stringResource(R.string.tv_setup_no_typed_modes))
            TvButton(
                label = stringResource(R.string.setup_change_server),
                onClick = actions.onChangeServer,
                style = TvButtonStyle.Primary,
                modifier = Modifier.tvArrivalTarget(arrival),
            )
        } else {
            TvOptionGroup(
                title = stringResource(R.string.tv_setup_mode_title),
                choices = typed.map { mode -> mode to mode.label(state.server) },
                selected = state.form.mode,
                onSelect = { mode -> actions.onEditForm { copy(mode = mode) } },
                arrival = arrival,
            )
            TvModeFields(state.form, actions.onEditForm, initialFocus == TvSetupFocus.Credential)
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
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
    credentialFocused: Boolean,
) {
    when (form.mode) {
        SeerrSignInMode.ApiKey ->
            TvTextField(
                value = form.apiKey,
                onValueChange = { value -> onEdit { copy(apiKey = value) } },
                label = stringResource(R.string.setup_api_key),
                secret = true,
                keyboardType = KeyboardType.Password,
                initiallyFocused = credentialFocused,
            )
        SeerrSignInMode.Local -> {
            TvTextField(
                value = form.email,
                onValueChange = { value -> onEdit { copy(email = value) } },
                label = stringResource(R.string.setup_email),
                keyboardType = KeyboardType.Email,
                initiallyFocused = credentialFocused,
            )
            TvPasswordField(form, onEdit)
        }
        SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> {
            TvTextField(
                value = form.username,
                onValueChange = { value -> onEdit { copy(username = value) } },
                label = stringResource(R.string.setup_username),
                initiallyFocused = credentialFocused,
            )
            TvPasswordField(form, onEdit)
        }
        // Filtered out above; a mode the LaunchedEffect is about to replace renders nothing for a frame.
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
    )
}
