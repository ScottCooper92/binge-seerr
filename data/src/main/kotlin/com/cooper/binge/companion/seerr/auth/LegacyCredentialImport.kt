package com.cooper.binge.companion.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.foundation.info
import com.cooper.homelab.core.foundation.warn
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.connection.ConnectionSecurity
import com.cooper.homelab.core.server.connection.SavedServer
import com.cooper.homelab.core.server.connection.ServerRegistry
import kotlinx.coroutines.flow.first
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/**
 * The one-time move of the saved connection from the file the released versions wrote ([legacy]) into the registry and the
 * login store. The old file is only read and stays for one release, so a rollback still works. The secret is copied as the
 * blob it is, so nothing is decrypted: one that no longer decrypts imports as a saved server that is signed out, as does a
 * server the user had signed out of. [marker] makes it run once, so a user who signs out is not signed back in from the old
 * file. The server is saved under [serverId], so it has the id the rest of the app already keys by. This class only reads the old keys,
 * which the legacy store spells too.
 */
class LegacyCredentialImport(
    private val legacy: DataStore<Preferences>,
    private val registry: ServerRegistry,
    private val logins: LoginStore<SeerrLogin>,
    private val marker: DataStore<Preferences>,
    private val serverId: ServerIdentity,
    private val logger: Logger,
) {
    suspend fun importOnce() {
        if (marker.data.first()[DONE] == true) return
        if (registry.current().servers.isEmpty()) importFile(legacy.data.first())
        marker.edit { it[DONE] = true }
    }

    private suspend fun importFile(old: Preferences) {
        val url = old[BASE_URL]?.takeIf { it.isNotBlank() } ?: old[SIGNED_OUT_URL]?.takeIf { it.isNotBlank() } ?: return
        val address = ServerAddress.parse(url)
        if (address == null) {
            logger.warn(TAG, "legacy-address-unreadable")
            return
        }
        val id = serverId.currentServerId()
        val signedIn = old[BASE_URL]?.isNotBlank() == true
        val fields = if (signedIn) old.fields() else null
        val secret = old[SECRET]?.takeIf { it.isNotBlank() }
        if (fields != null && secret != null) logins.importEncrypted(id, fields, secret)
        registry.restore(SavedServer(id, address, address.host, SEERR_KIND, ConnectionSecurity()))
        logger.info(
            TAG,
            "legacy-connection-imported",
            listOf(
                LogField.Server(id),
                LogField.Flag(
                    "signedIn",
                    fields != null && secret != null,
                ),
            ),
        )
    }

    /** The fields of the stored row in the shape [SeerrLoginCodec] writes, or null for a row that is not a login. */
    private fun Preferences.fields(): Map<String, String>? {
        val variant = this[VARIANT] ?: "Unknown"
        return when (this[AUTH_TYPE]) {
            SeerrLoginCodec.KIND_API_KEY -> mapOf(SeerrLoginCodec.KIND to SeerrLoginCodec.KIND_API_KEY, SeerrLoginCodec.VARIANT to variant)
            SeerrLoginCodec.KIND_SESSION ->
                this[SESSION_USER_ID]?.let { userId ->
                    mapOf(
                        SeerrLoginCodec.KIND to SeerrLoginCodec.KIND_SESSION,
                        SeerrLoginCodec.USER_ID to userId.toString(),
                        SeerrLoginCodec.SHARED to (this[SESSION_SHARED] ?: false).toString(),
                        SeerrLoginCodec.VARIANT to variant,
                    )
                }
            else -> null
        }
    }

    private companion object {
        const val TAG = "LegacyCredentialImport"
        val BASE_URL = stringPreferencesKey("base_url")
        val AUTH_TYPE = stringPreferencesKey("auth_type")
        val SECRET = stringPreferencesKey("secret")
        val SESSION_USER_ID = intPreferencesKey("session_user_id")
        val SESSION_SHARED = booleanPreferencesKey("session_shared")
        val VARIANT = stringPreferencesKey("variant")
        val SIGNED_OUT_URL = stringPreferencesKey("signed_out_url")
        val DONE = booleanPreferencesKey("legacy_import_done")
    }
}
