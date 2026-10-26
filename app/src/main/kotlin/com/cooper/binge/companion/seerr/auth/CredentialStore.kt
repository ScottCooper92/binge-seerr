package com.cooper.binge.companion.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val AUTH_TYPE_API_KEY = "api_key"
private const val AUTH_TYPE_SESSION = "session"

/**
 * The saved connection. The base URL and the auth kind are stored in the clear; the secret — the
 * API key or the session cookie — goes through [SecretCipher]. A row is only reported as
 * credentials when the URL is present and the secret decrypts, so a lost Keystore key reads as
 * "not connected" rather than as a connection that fails every call. The decrypt runs on [io]: the
 * first screen collects [credentials] on the main thread, and a Keystore decrypt reads from disk.
 */
class CredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    val credentials: Flow<SeerrCredentials?> =
        dataStore.data
            .map { prefs ->
                val baseUrl = prefs[Keys.BASE_URL]
                if (baseUrl.isNullOrBlank()) null else prefs.toAuth()?.let { SeerrCredentials(baseUrl, it, prefs.toVariant()) }
            }.flowOn(io)
            .distinctUntilChanged()

    /**
     * The server the user signed out of, while nothing is saved, so setup can open on its sign-in. A new connection or a
     * disconnect forgets it.
     */
    val signedOutServer: Flow<String?> = dataStore.data.map { it[Keys.SIGNED_OUT_URL] }.distinctUntilChanged()

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
        val secret = attempt { cipher.encrypt(plaintext) }.getOrElse { return false }
        dataStore.edit { prefs ->
            prefs.remove(Keys.SIGNED_OUT_URL)
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
                    prefs[Keys.SESSION_SHARED] = auth.shared
                }
            }
        }
        return true
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /** Forgets the connection, secret and all, but keeps its address as [signedOutServer]. */
    suspend fun signOut() {
        dataStore.edit { prefs ->
            val baseUrl = prefs[Keys.BASE_URL]
            prefs.clear()
            baseUrl?.let { prefs[Keys.SIGNED_OUT_URL] = it }
        }
    }

    private fun Preferences.toVariant(): SeerrVariant =
        this[Keys.VARIANT]?.let { stored -> attempt { SeerrVariant.valueOf(stored) }.getOrNull() } ?: SeerrVariant.Unknown

    private fun Preferences.toAuth(): SeerrAuth? =
        when (this[Keys.AUTH_TYPE]) {
            AUTH_TYPE_API_KEY -> decryptSecret()?.let { SeerrAuth.ApiKey(it) }
            AUTH_TYPE_SESSION -> {
                val cookie = decryptSecret()
                val userId = this[Keys.SESSION_USER_ID]
                if (cookie != null && userId != null) SeerrAuth.Session(cookie, userId, this[Keys.SESSION_SHARED] ?: false) else null
            }
            else -> null
        }

    private fun Preferences.decryptSecret(): String? = this[Keys.SECRET]?.takeIf { it.isNotBlank() }?.let { cipher.decrypt(it) }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val AUTH_TYPE = stringPreferencesKey("auth_type")
        val SECRET = stringPreferencesKey("secret")
        val SESSION_USER_ID = intPreferencesKey("session_user_id")
        val SESSION_SHARED = booleanPreferencesKey("session_shared")
        val VARIANT = stringPreferencesKey("variant")
        val SIGNED_OUT_URL = stringPreferencesKey("signed_out_url")
    }
}
