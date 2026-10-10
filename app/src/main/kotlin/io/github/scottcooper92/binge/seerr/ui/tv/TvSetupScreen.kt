package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.DISABLED_ALPHA
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupNotice
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
    offerHandOff: Boolean = false,
) {
    // An address from a phone waits on this screen's form for the user to go on, not on the code page (#907, #1084).
    val awaitingTv = (state as? SetupUiState.Address)?.let { it.awaitingConfirm || it.awaitingCleartextConsent } == true
    // The phone does the setup; typing with the remote is the fallback, chosen on purpose and left on purpose.
    var manual by rememberSaveable { mutableStateOf(!offerHandOff || awaitingTv) }
    val scanInstead = {
        manual = false
        actions.onStartHandOff()
    }
    // Back from the fallback goes back to the code, as "Scan a QR" does, rather than out of the app.
    val typing = manual && offerHandOff && (state is SetupUiState.Address || state is SetupUiState.SignIn && state.link == null)
    LaunchedEffect(awaitingTv) { if (awaitingTv) manual = true }
    BackHandler(enabled = typing) {
        if (state is SetupUiState.Address) scanInstead() else manual = false
    }
    when {
        // The home swaps to the connected plate on the credentials landing; this is the frame in between.
        state == SetupUiState.Loading || state is SetupUiState.Connected -> TvLoadingPlate(modifier = modifier)
        // A link flow takes the whole page: the code is the only thing to read, and the only thing to do
        // is wait or back out.
        state is SetupUiState.SignIn && state.link != null -> TvSetupLinkPlate(state.link, actions.onCancelLink, modifier)
        !manual ->
            TvSetupCodePage(
                state = state,
                actions = actions,
                onManual = {
                    manual = true
                    actions.onCancelHandOff()
                },
                modifier = modifier,
            )
        state is SetupUiState.Address ->
            TvSetupAddressPage(state, actions.withStartHandOff(scanInstead), modifier, initialFocus)
        state is SetupUiState.SignIn -> TvSetupSignInStep(state, actions, modifier, initialFocus, onScan = { manual = false })
    }
}

/** [this], with the typed form's "Scan a QR" also leaving the fallback for the code page. */
private fun SetupActions.withStartHandOff(start: () -> Unit) =
    SetupActions(
        onEditAddress = onEditAddress,
        onInspect = onInspect,
        onChangeServer = onChangeServer,
        onEditForm = onEditForm,
        onConnect = onConnect,
        onPlexLaunched = onPlexLaunched,
        onCancelLink = onCancelLink,
        onRequestPasswordReset = onRequestPasswordReset,
        onAllowCleartext = onAllowCleartext,
        onStartHandOff = start,
        onCancelHandOff = onCancelHandOff,
        onOfferSignInCode = onOfferSignInCode,
        onLocalNetworkChanged = onLocalNetworkChanged,
    )

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
    onScan: () -> Unit = {},
) {
    val offered = state.server.modes.filter { it.finishableOnTv }
    // The form opens on the server's first mode, which may be one this surface cannot finish.
    val modeOffered = state.form.mode in offered
    LaunchedEffect(modeOffered, offered) {
        if (!modeOffered && offered.isNotEmpty()) actions.onEditForm { copy(mode = offered.first()) }
    }
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    val commit = remember { FocusRequester() }
    val canSignIn = state.form.canSubmit && !state.isConnecting && state.link == null
    TvFormPage(
        headline = state.server.title,
        // The code page's copy, so stepping between it and this form moves nothing on the left.
        body = stringResource(R.string.tv_setup_sign_in_scan_body),
        icon = Icons.Filled.Lock,
        modifier = modifier,
        buttonBar = true,
        // Down from the fields lands on Sign in while it can be pressed, and on Change server while it can't.
        pinnedEntry = commit.takeIf { canSignIn && offered.isNotEmpty() },
        pinnedAction = {
            TvSignInButtons(state, actions, offered.isNotEmpty(), canSignIn, commit, arrival.takeIf { offered.isEmpty() }, initialFocus)
        },
        // Back to the code page, where a phone can finish this.
        copyAction = { TvButton(label = stringResource(R.string.tv_setup_send_from_phone), onClick = onScan) },
    ) {
        if (offered.isEmpty()) {
            // Change server is in the button bar, the one way on from a server this TV can't sign in to.
            TvFormNote(stringResource(R.string.tv_setup_no_modes_here))
        } else {
            TvTabs(
                choices = offered.map { mode -> mode to mode.label(state.server) },
                selected = state.form.mode,
                onSelect = { mode -> actions.onEditForm { copy(mode = mode) } },
                // Where the page lands: the first thing to choose is how to sign in.
                arrival = arrival,
            )
            TvModeFields(state.form, state.server, actions.onEditForm, initialFocus == TvSetupFocus.Credential, onDone = actions.onConnect)
            state.error?.let { error -> TvFormNote(stringResource(error.messageRes()), tone = TvFormNoteTone.Error) }
            state.notice?.let { notice -> TvFormNote(stringResource(notice.messageRes()), tone = notice.tone) }
        }
    }
}

/**
 * The sign-in step's commit and its way back, on the right of the button bar where the remote ends up after the
 * fields. With no mode this TV can finish, Change server is the only way on, so it leads and the page lands on it.
 */
@Composable
private fun TvSignInButtons(
    state: SetupUiState.SignIn,
    actions: SetupActions,
    hasModes: Boolean,
    canSignIn: Boolean,
    commit: FocusRequester,
    arrival: TvArrivalFocus?,
    initialFocus: TvSetupFocus?,
) {
    if (hasModes) {
        TvButton(
            label = stringResource(if (state.isConnecting) R.string.tv_setup_connecting else state.form.mode.submitLabelRes()),
            onClick = actions.onConnect,
            style = TvButtonStyle.Primary,
            enabled = canSignIn,
            initiallyFocused = initialFocus == TvSetupFocus.Connect,
            // Dimmed as a whole, so it can't be read as the outlined button beside it, and one width for every mode's
            // label, so Change server beside it stays where it is.
            modifier =
                Modifier
                    .focusRequester(commit)
                    .widthIn(min = dimensionResource(R.dimen.tv_form_commit_min_width))
                    .alpha(if (canSignIn) 1f else DISABLED_ALPHA),
        )
    }
    TvButton(
        label = stringResource(R.string.setup_change_server),
        onClick = actions.onChangeServer,
        style = if (hasModes) TvButtonStyle.Secondary else TvButtonStyle.Primary,
        modifier = arrival?.let { Modifier.tvArrivalTarget(it) } ?: Modifier,
    )
}

/** The fields the chosen mode needs, and only those: a key, or an identity and a password. */
@Composable
private fun TvModeFields(
    form: SignInForm,
    server: SetupServer,
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
    credentialFocused: Boolean,
    onDone: () -> Unit = {},
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
                onDone = { if (form.canSubmit) onDone() },
            )
            // TvTextField has no supporting slot, and this has to stay readable while the user
            // fetches the key — so it is the note the page already uses for what it wants said.
            TvFormNote(stringResource(R.string.setup_api_key_hint))
            TvFormNote(stringResource(R.string.setup_api_key_admin_note))
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
            TvPasswordField(form, onEdit, onDone)
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
            TvPasswordField(form, onEdit, onDone)
        }
        // Neither needs typed fields: pressing Connect below mints its code, and the plate above takes over.
        SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect -> Unit
    }
}

@Composable
private fun TvPasswordField(
    form: SignInForm,
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
    onDone: () -> Unit,
) {
    TvTextField(
        value = form.password,
        onValueChange = { value -> onEdit { copy(password = value) } },
        label = stringResource(R.string.setup_password),
        secret = true,
        keyboardType = KeyboardType.Password,
        contentType = ContentType.Password,
        onDone = { if (form.canSubmit) onDone() },
    )
}

/** A reset email that went is good news; a session the server rejected is only the reason the form is up. */
private val SetupNotice.tone: TvFormNoteTone
    get() =
        when (this) {
            SetupNotice.ResetEmailSent -> TvFormNoteTone.Success
            SetupNotice.SessionRejected -> TvFormNoteTone.Neutral
        }
