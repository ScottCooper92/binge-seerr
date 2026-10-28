package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvTabRow
import com.binge.designsystem.tv.component.TvTabUi
import com.binge.designsystem.tv.component.TvTextField
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.ui.SetupActions
import com.cooper.binge.companion.seerr.ui.SetupNotice
import com.cooper.binge.companion.seerr.ui.SetupServer
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.ui.SignInForm
import com.cooper.binge.companion.seerr.ui.label
import com.cooper.binge.companion.seerr.ui.messageRes
import kotlinx.coroutines.delay
import com.binge.designsystem.R as DesR

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
        state is SetupUiState.SignIn ->
            TvSetupSignInStep(
                state = state,
                actions = actions,
                modifier = modifier,
                initialFocus = initialFocus,
                scanSelected = offerHandOff && !manual,
                onScan = if (offerHandOff) ({ manual = false }) else null,
                onType = { manual = true },
            )
        state is SetupUiState.Address ->
            TvSetupAddressPage(
                state = state,
                actions = actions,
                modifier = modifier,
                initialFocus = initialFocus,
                scanSelected = offerHandOff && !manual,
                onScan = if (offerHandOff) ({ manual = false }) else null,
                onType = {
                    manual = true
                    actions.onCancelHandOff()
                },
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
    scanSelected: Boolean = false,
    onScan: (() -> Unit)? = null,
    onType: () -> Unit = {},
) {
    val offered = state.server.modes.filter { it.finishableOnTv }
    // The form opens on the server's first mode, which may be one this surface cannot finish.
    val modeOffered = state.form.mode in offered
    LaunchedEffect(modeOffered, offered) {
        if (!modeOffered && offered.isNotEmpty()) actions.onEditForm { copy(mode = offered.first()) }
    }
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    val typing = !scanSelected && offered.isNotEmpty()
    // A tab that finishes on another device asks for its code once it has been rested on, not as the remote passes over it.
    val mintsCode = !scanSelected && state.form.mode in CodeModes
    var requested by remember(mintsCode, state.form.mode) { mutableStateOf(false) }
    LaunchedEffect(mintsCode, state.form.mode) {
        if (mintsCode) {
            delay(CODE_REQUEST_DELAY_MILLIS)
            requested = true
            actions.onConnect()
        }
    }
    // Back abandons a code that is waiting, as Cancel does, rather than leaving the app with the link still open.
    BackHandler(enabled = state.link != null, onBack = actions.onCancelLink)
    TvSignInTabsPage(
        // Put up by the app itself when the server rejected the session (#810): it says so, then the same tabs.
        headline =
            if (state.notice == SetupNotice.SessionRejected) stringResource(R.string.hub_unauthorized_headline) else state.server.title,
        modifier = modifier,
        // Where Change server is on every tab, so the remote finds it without looking.
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
                TvChangeServerButton(actions, leads = !typing && onScan == null, arrival = arrival.takeIf { !typing && onScan == null })
                // Leaving the server altogether is only offered where the app put this page up for a session the server rejected.
                actions.onDisconnect?.let { disconnect ->
                    TvButton(label = stringResource(R.string.hub_disconnect), onClick = disconnect, style = TvButtonStyle.TextDestructive)
                }
            }
        },
        tabs = { TvSignInTabs(state, actions, offered, scanSelected, onScan, onType, arrival) },
    ) {
        TvSignInBody(state, actions, offered, scanSelected, mintsCode, requested, initialFocus)
    }
}

/**
 * Text tabs, selection following focus: the selected tab is the filled one, so it needs neither a glyph nor a tick, and
 * five of them fit a line. The page lands here, since the first thing to choose is how to sign in.
 */
@Composable
private fun TvSignInTabs(
    state: SetupUiState.SignIn,
    actions: SetupActions,
    offered: List<SeerrSignInMode>,
    scanSelected: Boolean,
    onScan: (() -> Unit)?,
    onType: () -> Unit,
    arrival: TvArrivalFocus,
) {
    TvTabRow(
        tabs =
            listOfNotNull(onScan?.let { TvTabUi(key = SCAN_KEY, label = stringResource(R.string.tv_setup_send_from_phone)) }) +
                offered.map { mode -> TvTabUi(key = mode.name, label = mode.label(state.server)) },
        selectedKey = if (scanSelected) SCAN_KEY else state.form.mode.name,
        onSelect = { key ->
            // Leaving a tab leaves its waiting code behind.
            if (state.link != null) actions.onCancelLink()
            if (key == SCAN_KEY) {
                onScan?.invoke()
            } else {
                offered.firstOrNull { it.name == key }?.let { mode ->
                    onType()
                    actions.onEditForm { copy(mode = mode) }
                }
            }
        },
        arrival = arrival,
    )
}

/** What the chosen tab shows: its waiting code, the code for the phone, or the fields it needs and the commit under them. */
@Composable
private fun ColumnScope.TvSignInBody(
    state: SetupUiState.SignIn,
    actions: SetupActions,
    offered: List<SeerrSignInMode>,
    scanSelected: Boolean,
    mintsCode: Boolean,
    requested: Boolean,
    initialFocus: TvSetupFocus?,
) {
    // Connecting while the request is pending or in flight; a code abandoned with Back is neither, so it gets the commit.
    val connecting = mintsCode && state.error == null && (!requested || state.isConnecting)
    when {
        state.link != null -> TvSetupLinkContent(state.link)
        scanSelected -> {
            state.notice?.let { notice -> TvFormNote(stringResource(notice.messageRes()), tone = notice.tone, centered = true) }
            TvSignInCodeContent(state, actions)
        }
        offered.isEmpty() -> TvFormNote(stringResource(R.string.tv_setup_no_modes_here))
        connecting -> TvFormNote(stringResource(R.string.tv_setup_connecting), centered = true)
        else -> {
            TvModeFields(
                form = state.form,
                server = state.server,
                onEdit = actions.onEditForm,
                credentialFocused = initialFocus == TvSetupFocus.Credential,
                onDone = actions.onConnect,
            )
            // A code tab has no button while its code is on the way: after a failure or a Back there is one way to try again.
            if (!connecting) TvSignInActions(state, actions, initialFocus)
            // Under the buttons, so Sign in stays where it is when a message comes and goes.
            state.error?.let { error -> TvFormNote(stringResource(error.messageRes()), tone = TvFormNoteTone.Error) }
            state.notice?.let { notice -> TvFormNote(stringResource(notice.messageRes()), tone = notice.tone) }
        }
    }
}

private const val SCAN_KEY = "scan"
private const val CODE_REQUEST_DELAY_MILLIS = 500L

/**
 * The way back to the address, pinned at the foot of the page. A label alone, in the destructive colour: it drops the
 * server and what was set up on it. With no mode this TV can finish and no code tab it is the only way on, so it
 * [leads] and the page lands on it.
 */
@Composable
private fun TvChangeServerButton(
    actions: SetupActions,
    leads: Boolean,
    arrival: TvArrivalFocus?,
) {
    TvButton(
        label = stringResource(R.string.setup_change_server),
        onClick = actions.onChangeServer,
        style = if (leads) TvButtonStyle.Primary else TvButtonStyle.TextDestructive,
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
                onImeAction = { if (form.canSubmit) onDone() },
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
        onImeAction = { if (form.canSubmit) onDone() },
    )
}

/** A reset email that went is good news; a session the server rejected is only the reason the form is up. */
private val SetupNotice.tone: TvFormNoteTone
    get() =
        when (this) {
            SetupNotice.ResetEmailSent -> TvFormNoteTone.Success
            SetupNotice.SessionRejected -> TvFormNoteTone.Neutral
        }
