package com.cooper.binge.companion.seerr.auth

import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.homelab.core.server.connection.CredentialCodec
import com.cooper.homelab.core.server.connection.CredentialParts

/**
 * How the user is signed in to one server: the secret and what goes with it. The address is not part of it, since the saved
 * server holds that. [SeerrCredentials] is a [SeerrLogin] and an address.
 *
 * [boundTo] is the address the login was saved for, or null for a row written before it was kept. The login and the address are
 * two writes, so a reader can see one without the other; a login is read as the saved server's only while the two agree.
 * [profile] is the [StoredProfile] last read for the server, as the JSON it is kept in, or null before one was read.
 */
data class SeerrLogin(
    val auth: SeerrAuth,
    val variant: SeerrVariant,
    val boundTo: String? = null,
    val profile: String? = null,
)

/**
 * A [SeerrLogin] cut into the fields the shared library keeps in the clear and the secret it encrypts. The field names and
 * values are an on-disk format, and [LegacyCredentialImport] writes the same ones for a row the released version kept.
 */
object SeerrLoginCodec : CredentialCodec<SeerrLogin> {
    const val KIND = "kind"
    const val USER_ID = "userId"
    const val SHARED = "shared"
    const val VARIANT = "variant"
    const val ADDRESS = "address"
    const val PROFILE = "profile"
    const val KIND_API_KEY = "api_key"
    const val KIND_SESSION = "session"

    override fun split(credential: SeerrLogin): CredentialParts =
        when (val auth = credential.auth) {
            is SeerrAuth.ApiKey ->
                CredentialParts(withAddress(mapOf(KIND to KIND_API_KEY, VARIANT to credential.variant.name), credential), auth.key)
            is SeerrAuth.Session ->
                CredentialParts(
                    withAddress(
                        mapOf(
                            KIND to KIND_SESSION,
                            USER_ID to auth.userId.toString(),
                            SHARED to auth.shared.toString(),
                            VARIANT to credential.variant.name,
                        ),
                        credential,
                    ),
                    auth.cookie,
                )
        }

    private fun withAddress(
        fields: Map<String, String>,
        credential: SeerrLogin,
    ): Map<String, String> {
        val bound = credential.boundTo?.let { fields + (ADDRESS to it) } ?: fields
        return credential.profile?.let { bound + (PROFILE to it) } ?: bound
    }

    override fun join(
        fields: Map<String, String>,
        secret: String,
    ): SeerrLogin? {
        val variant = SeerrVariant.entries.firstOrNull { it.name == fields[VARIANT] } ?: SeerrVariant.Unknown
        val auth =
            when (fields[KIND]) {
                KIND_API_KEY -> SeerrAuth.ApiKey(secret)
                KIND_SESSION -> fields[USER_ID]?.toIntOrNull()?.let { SeerrAuth.Session(secret, it, fields[SHARED] == "true") }
                else -> null
            }
        return auth?.let { SeerrLogin(it, variant, fields[ADDRESS], fields[PROFILE]) }
    }
}
