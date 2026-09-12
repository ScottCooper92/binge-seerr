package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val AUTH_TYPE_API_KEY = "api_key"
private const val AUTH_TYPE_SESSION = "session"

/**
 * The saved connection. The base URL and the auth kind are stored in the clear; the secret — the
 * API key or the session cookie — goes through [SecretCipher]. A row is only reported as
 * credentials when the URL is present and the secret decrypts, so a lost Keystore key reads as
 * "not connected" rather than as a connection that fails every call.
 */
class CredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    val credentials: Flow<SeerrCredentials?> =
        dataStore.data
            .map { prefs ->
                val baseUrl = prefs[Keys.BASE_URL]
                if (baseUrl.isNullOrBlank()) null else prefs.toAuth()?.let { SeerrCredentials(baseUrl, it, prefs.toVariant()) }
            }.distinctUntilChanged()

    /**
     * Encrypts and persists [credentials]. Returns false, storing nothing, if encryption fails —
     * a flaky vendor keymaster can throw from the Keystore — so a failure is an unsaved connection
     * rather than a half-written row.
     */
    suspend fun save(credentials: SeerrCredentials): Boolean {
        val plaintext =
            when (val auth = credentials.auth) {
                is SeerrAuth.ApiKey -> auth.key
                is SeerrAuth.Session -> auth.cookie
            }
        val secret = runCatching { cipher.encrypt(plaintext) }.getOrElse { return false }
        dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = credentials.baseUrl
            prefs[Keys.VARIANT] = credentials.variant.name
            prefs[Keys.SECRET] = secret
            when (val auth = credentials.auth) {
                is SeerrAuth.ApiKey -> {
                    prefs[Keys.AUTH_TYPE] = AUTH_TYPE_API_KEY
                    prefs.remove(Keys.SESSION_USER_ID)
                }
                is SeerrAuth.Session -> {
                    prefs[Keys.AUTH_TYPE] = AUTH_TYPE_SESSION
                    prefs[Keys.SESSION_USER_ID] = auth.userId
                }
            }
        }
        return true
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toVariant(): SeerrVariant =
        this[Keys.VARIANT]?.let { stored -> runCatching { SeerrVariant.valueOf(stored) }.getOrNull() } ?: SeerrVariant.Unknown

    private fun Preferences.toAuth(): SeerrAuth? =
        when (this[Keys.AUTH_TYPE]) {
            AUTH_TYPE_API_KEY -> decryptSecret()?.let { SeerrAuth.ApiKey(it) }
            AUTH_TYPE_SESSION -> {
                val cookie = decryptSecret()
                val userId = this[Keys.SESSION_USER_ID]
                if (cookie != null && userId != null) SeerrAuth.Session(cookie, userId) else null
            }
            else -> null
        }

    private fun Preferences.decryptSecret(): String? = this[Keys.SECRET]?.takeIf { it.isNotBlank() }?.let { cipher.decrypt(it) }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val AUTH_TYPE = stringPreferencesKey("auth_type")
        val SECRET = stringPreferencesKey("secret")
        val SESSION_USER_ID = intPreferencesKey("session_user_id")
        val VARIANT = stringPreferencesKey("variant")
    }
}
