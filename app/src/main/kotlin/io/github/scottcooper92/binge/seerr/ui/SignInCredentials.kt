package io.github.scottcooper92.binge.seerr.ui

import android.credentials.GetCredentialException
import android.credentials.GetCredentialRequest
import android.credentials.GetCredentialResponse
import android.os.Build
import android.os.Bundle
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CredentialRequestData
import androidx.compose.ui.semantics.credentialRequest
import androidx.compose.ui.semantics.semantics
import androidx.credentials.Credential
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential

/** A login a credential provider handed back: the saved account and its password. */
internal data class SavedLogin(
    val id: String,
    val password: String,
)

/**
 * The account and password out of a provider's answer, or null for anything this form cannot use —
 * a passkey, a credential that failed to parse, a saved login with no account name on it.
 *
 * The bundle is read by androidx rather than by key here: the keys are its internals, and it already
 * refuses a password credential carrying no password. Taking a type and a bundle rather than the
 * `android.credentials` wrapper is what keeps this testable below API 34.
 */
internal fun savedLoginFrom(
    type: String,
    data: Bundle,
): SavedLogin? =
    (Credential.createFrom(type, data) as? PasswordCredential)
        ?.takeIf { it.id.isNotEmpty() }
        ?.let { SavedLogin(it.id, it.password) }

/**
 * The saved-password request, assembled from androidx's option rather than from bundle keys spelled
 * out here, so a provider parses it exactly as it parses every other app's.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun passwordCredentialRequest(): GetCredentialRequest {
    val option = GetPasswordOption()
    val platformOption =
        android.credentials.CredentialOption
            .Builder(option.type, option.requestData, option.candidateQueryData)
            .setIsSystemProviderRequired(option.isSystemProviderRequired)
            .setAllowedProviders(option.allowedProviders)
            .build()
    return GetCredentialRequest
        .Builder(Bundle())
        .addCredentialOption(platformOption)
        .build()
}

/**
 * Offers the field's saved logins through the platform's credential providers, so a server nobody
 * memorises can be signed into from a password manager.
 *
 * `android.credentials` starts at API 34. Below it this is [Modifier] and the field's autofill
 * content type is the whole hand-off, which reaches the same password managers by the older route.
 */
@Composable
internal fun Modifier.savedLoginRequest(onLogin: (SavedLogin) -> Unit): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        this.then(credentialRequestSemantics(onLogin))
    } else {
        this
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun credentialRequestSemantics(onLogin: (SavedLogin) -> Unit): Modifier {
    val current = rememberUpdatedState(onLogin)
    val data =
        remember {
            CredentialRequestData(
                passwordCredentialRequest(),
                object : OutcomeReceiver<GetCredentialResponse, GetCredentialException> {
                    override fun onResult(result: GetCredentialResponse) {
                        val credential = result.credential
                        savedLoginFrom(credential.type, credential.data)?.let(current.value)
                    }

                    // A dismissed picker and a provider with nothing saved arrive here alike, and
                    // both leave the field exactly as the user typed it.
                    override fun onError(error: GetCredentialException) = Unit
                },
            )
        }
    return Modifier.semantics { credentialRequest = data }
}
