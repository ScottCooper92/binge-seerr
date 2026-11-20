package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.res.stringResource
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

/** What a failure of the shared machine says on the setup form: the same line [SetupError] gives it on the old path. */
@Composable
internal fun seerrErrorText(error: ConnectError): String? = error.toSetupError()?.let { stringResource(it.messageRes()) }

/**
 * Seerr's own parts of the sign-in step, in the slots the shared one leaves for them: the note under an API key, the forgot-password
 * button under a local account's fields, the notice (a reset email sent, a session the server turned down) after the error, and a
 * saved login offered on the field that names the account.
 *
 * [onEdit] writes what a credential provider returns into the form.
 */
internal fun seerrSignInOptions(
    notice: SetupNotice?,
    resetCoolingDown: Boolean,
    onRequestPasswordReset: () -> Unit,
    onEdit: ((SignInForm.() -> SignInForm) -> Unit),
): ConnectSignInOptions<SetupServer> =
    ConnectSignInOptions(
        tabEdgePadding = null,
        afterFields = { state -> SeerrAfterFields(state, resetCoolingDown, onRequestPasswordReset) },
        afterError = { _ -> notice?.let { SeerrNotice(it) } },
        fieldModifier = { field -> field.savedLogin(onEdit) },
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
