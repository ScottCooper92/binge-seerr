package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An off-device copy of the connection, for the one case the local copy cannot serve: a new device.
 *
 * [CredentialStore] seals its secret with a Keystore key bound to the hardware that generated it,
 * so a transferred row decrypts to nothing and the user signs in again. The carrier holds the
 * secret in the clear instead, and leans on the carrier's own at-rest protection rather than ours.
 * Nothing read back is trusted until it has been proved against the server — see [ConnectionRestore].
 */
interface ConnectionCarrier {
    /** Mirrors the saved connection out. Best-effort: a carrier that refuses must not fail the save. */
    suspend fun put(credentials: SeerrCredentials)

    /** The carried connection as stored, unproven. Null when there is none, or it cannot be read. */
    suspend fun read(): SeerrCredentials?

    suspend fun clear()
}

/** The carrier on a device with nothing to carry it. Every call is a no-op. */
object NoConnectionCarrier : ConnectionCarrier {
    override suspend fun put(credentials: SeerrCredentials) = Unit

    override suspend fun read(): SeerrCredentials? = null

    override suspend fun clear() = Unit
}

private const val KIND_API_KEY = "api_key"
private const val KIND_SESSION = "session"

/**
 * The carried form. Spelled out rather than serialised from [SeerrCredentials] so that a field
 * added to the connection cannot silently change what devices already carry.
 */
@Serializable
private data class CarriedConnection(
    @SerialName("url") val baseUrl: String,
    @SerialName("kind") val kind: String,
    @SerialName("secret") val secret: String,
    @SerialName("user") val userId: Int? = null,
    @SerialName("variant") val variant: String? = null,
)

private val carrierJson = Json { ignoreUnknownKeys = true }

/** The bytes a carrier stores for [credentials]. */
internal fun encodeCarriedConnection(credentials: SeerrCredentials): ByteArray {
    val carried =
        when (val auth = credentials.auth) {
            is SeerrAuth.ApiKey -> CarriedConnection(credentials.baseUrl, KIND_API_KEY, auth.key, variant = credentials.variant.name)
            is SeerrAuth.Session ->
                CarriedConnection(credentials.baseUrl, KIND_SESSION, auth.cookie, auth.userId, credentials.variant.name)
        }
    return carrierJson.encodeToString(carried).toByteArray(Charsets.UTF_8)
}

/**
 * [bytes] back into a connection, or null for anything unusable — a payload this version does not
 * understand, a kind it does not know, a session with no user. A carrier holds what an older build
 * wrote, so refusing beats guessing.
 */
internal fun decodeCarriedConnection(bytes: ByteArray): SeerrCredentials? {
    val carried =
        runCatching { carrierJson.decodeFromString<CarriedConnection>(bytes.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return null
    if (carried.baseUrl.isBlank() || carried.secret.isBlank()) return null
    val auth =
        when (carried.kind) {
            KIND_API_KEY -> SeerrAuth.ApiKey(carried.secret)
            KIND_SESSION -> carried.userId?.let { SeerrAuth.Session(carried.secret, it) }
            else -> null
        } ?: return null
    val variant = carried.variant?.let { name -> runCatching { SeerrVariant.valueOf(name) }.getOrNull() } ?: SeerrVariant.Unknown
    return SeerrCredentials(carried.baseUrl, auth, variant)
}
