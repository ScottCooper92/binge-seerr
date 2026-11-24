package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeTextButton
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.FieldSpec
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.presentation.connect.SignInMode
import com.cooper.homelab.core.uiandroid.connect.ConnectCopy
import com.cooper.homelab.core.uiandroid.connect.ConnectLinkCopy
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInOptions

/** The Seerr mode behind [this] and the Seerr server read, or null for a mode or server this app did not make. */
private fun ProbedServer<*>.seerr(mode: SignInMode): Pair<SeerrSignInMode, SetupServer>? {
    val form = (mode as? SeerrMode)?.mode
    val server = preview as? SetupServer
    return if (form != null && server != null) form to server else null
}

/**
 * The words of the shared sign-in step, in the strings the setup form already uses: each tab, field label, placeholder and hint, and the
 * label of the commit. The Jellyfin and Emby tabs carry the server's own name where the administrator set one.
 */
internal fun seerrConnectCopy(): ConnectCopy =
    ConnectCopy(
        modeLabel = { server, mode -> server.seerr(mode)?.let { (kind, setup) -> kind.label(setup) }.orEmpty() },
        fieldLabel = { _, _, field -> stringResource(field.labelRes()) },
        submitLabel = { mode -> (mode as? SeerrMode)?.let { stringResource(it.mode.submitLabelRes()) } },
        modeHint = { _, mode ->
            when ((mode as? SeerrMode)?.mode) {
                SeerrSignInMode.Plex -> stringResource(R.string.setup_plex_hint)
                SeerrSignInMode.QuickConnect -> stringResource(R.string.setup_quick_connect_hint)
                else -> null
            }
        },
        fieldHint = { _, _, field -> if (field.id == FIELD_API_KEY) stringResource(R.string.setup_api_key_hint) else null },
        fieldPlaceholder = { server, mode, field ->
            when (field.id) {
                FIELD_EMAIL -> stringResource(R.string.placeholder_email)
                FIELD_USERNAME ->
                    server.seerr(mode)?.let { (kind, setup) ->
                        stringResource(R.string.setup_username_placeholder, kind.label(setup))
                    }
                else -> null
            }
        },
    )

private fun FieldSpec.labelRes(): Int =
    when (id) {
        FIELD_API_KEY -> R.string.setup_api_key
        FIELD_EMAIL -> R.string.setup_email
        FIELD_USERNAME -> R.string.setup_username
        else -> R.string.setup_password
    }

/**
 * What a failure of the shared machine says on the setup form: the line [SetupError] gives it. [address] is set on
 * the address step, where an address a phone sent that is off the home network reads as such ([forAddress]).
 */
@Composable
internal fun seerrErrorText(
    error: ConnectError,
    address: String? = null,
    received: Boolean = false,
): String? =
    error
        .toSetupError()
        ?.let { if (address != null) it.forAddress(address, received) else it }
        ?.let { stringResource(it.messageRes()) }

/**
 * Seerr's own parts of the sign-in step, in the slots the shared one leaves for them: the note under an API key, the forgot-password
 * button under a local account's fields, the notice (a reset email sent, a session the server turned down) after the error, and a
 * saved login offered on the field that names the account. Each mode keeps its own typed text. [onDisconnect] is the way off a server that rejected the session (#810),
 * under the commit.
 *
 * [onEdit] writes what a credential provider returns into the form.
 */
internal fun seerrSignInOptions(
    notice: SetupNotice?,
    resetCoolingDown: Boolean,
    onRequestPasswordReset: () -> Unit,
    onEdit: ((SignInForm.() -> SignInForm) -> Unit),
    resetError: SetupError? = null,
    tabEdgePadding: Dp? = null,
    tabBleed: Dp = 0.dp,
    onDisconnect: (() -> Unit)? = null,
): ConnectSignInOptions<SetupServer> =
    ConnectSignInOptions<SetupServer>(
        tabEdgePadding = tabEdgePadding,
        tabBleed = tabBleed,
        afterFields = { state -> SeerrAfterFields(state, resetCoolingDown, onRequestPasswordReset) },
        afterError = { state ->
            // A refused reset is the app's own error: the machine's takes the line when it has one, as on the old form.
            if (state.error == null) resetError?.let { SeerrResetError(it) }
            notice?.let { SeerrNotice(it) }
        },
        fieldModifier = { field -> field.savedLogin(onEdit) },
        footerBelow = onDisconnect?.let { leave -> { _ -> DisconnectButton(leave) } },
    )

@Composable
private fun ColumnScope.SeerrAfterFields(
    state: ConnectState.SignIn<SetupServer>,
    resetCoolingDown: Boolean,
    onRequestPasswordReset: () -> Unit,
) {
    when ((state.form.mode as? SeerrMode)?.mode) {
        // The key is the server's own, so it carries the administrator's reach to every host this companion admits (#684).
        SeerrSignInMode.ApiKey ->
            Text(
                stringResource(R.string.setup_api_key_admin_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        SeerrSignInMode.Local ->
            if (state.server.preview.canResetPassword) {
                BingeTextButton(
                    label = stringResource(R.string.setup_forgot_password),
                    onClick = onRequestPasswordReset,
                    enabled = state.form.value(FIELD_EMAIL).isNotBlank() && !state.isConnecting && !resetCoolingDown,
                )
            }
        else -> Unit
    }
}

@Composable
private fun SeerrResetError(error: SetupError) {
    Text(
        stringResource(error.messageRes()),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SeerrNotice(notice: SetupNotice) {
    Text(
        stringResource(notice.messageRes()),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** A saved login on the field that names the account: the email of a local account, the username of a Jellyfin or Emby one. */
private fun FieldSpec.savedLogin(onEdit: (SignInForm.() -> SignInForm) -> Unit): Modifier =
    if (id == FIELD_EMAIL || id == FIELD_USERNAME) {
        val account = id
        Modifier.composed {
            Modifier.savedLoginRequest { login -> onEdit { with(account, login.id).with(FIELD_PASSWORD, login.password) } }
        }
    } else {
        Modifier
    }

/** The words of the sheet that shows a code to approve elsewhere, in the strings the old link sheet uses. */
@Composable
internal fun seerrLinkCopy(mode: SignInMode): ConnectLinkCopy =
    if ((mode as? SeerrMode)?.mode == SeerrSignInMode.Plex) {
        ConnectLinkCopy(
            title = stringResource(R.string.link_plex_title),
            body = stringResource(R.string.link_plex_body),
            openLabel = stringResource(R.string.link_plex_open),
        )
    } else {
        ConnectLinkCopy(
            title = stringResource(R.string.link_quick_connect_title),
            body = stringResource(R.string.link_quick_connect_body),
        )
    }
