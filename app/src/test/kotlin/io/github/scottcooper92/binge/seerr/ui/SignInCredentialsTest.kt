package io.github.scottcooper92.binge.seerr.ui

import android.os.Bundle
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a credential provider hands back, and what the sign-in form is willing to take from it. The
 * bundles are built by androidx rather than by key, which is how the provider builds them too.
 *
 * `android.credentials` starts at API 34, so the request half is only asked for there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SignInCredentialsTest {
    @Test
    fun `a saved password becomes the account and the secret the form fills`() {
        val saved = PasswordCredential("ada", "hunter2")

        assertEquals(SavedLogin("ada", "hunter2"), savedLoginFrom(saved.type, saved.data))
    }

    @Test
    fun `a passkey is not a login this form can sign in with`() {
        val login = savedLoginFrom(PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL, Bundle())

        assertNull(login)
    }

    @Test
    fun `a password credential that does not parse is refused rather than half-filled`() {
        val login = savedLoginFrom(PasswordCredential.TYPE_PASSWORD_CREDENTIAL, Bundle())

        assertNull(login)
    }

    @Test
    fun `a saved password with no account name on it is refused`() {
        val anonymous = PasswordCredential("", "hunter2")

        assertNull(savedLoginFrom(anonymous.type, anonymous.data))
    }

    @Test
    fun `the request asks for one saved password, carrying the bundles androidx built`() {
        val request = passwordCredentialRequest()

        val option = request.credentialOptions.single()
        assertEquals(PasswordCredential.TYPE_PASSWORD_CREDENTIAL, option.type)
        assertEquals(GetPasswordOption().candidateQueryData.keySet(), option.candidateQueryData.keySet())
    }
}
