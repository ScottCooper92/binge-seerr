package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrJellyfinLoginBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrLocalLoginBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrLoginRequest
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.isValidBaseUrl
import io.github.scottcooper92.binge.seerr.seerr.normaliseBaseUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The entered address cannot be parsed as a URL even after normalisation. */
class InvalidServerUrlException : IllegalArgumentException("Invalid Seerr server URL")

/** At-rest encryption of the credentials failed, so nothing was persisted. */
class CredentialsSaveException : IllegalStateException("Failed to store the Seerr credentials")

/** The login succeeded but the server set no session cookie, so there is nothing to keep. */
class NoSessionCookieException : IllegalStateException("Seerr login returned no session cookie")

/** No server is connected: the code the contract's `UNAUTHENTICATED` is mapped from. */
class NotConnectedException : IllegalStateException("No Seerr server is connected")

/**
 * The one connected server: connecting, logging in, disconnecting, and the API for the saved
 * credentials. The authenticated user (`auth/me`) is cached per credentials so the capability
 * handshake and the permission checks share one lookup per connection rather than one per call.
 */
class SeerrConnection(
    private val store: CredentialStore,
    private val apis: SeerrApiFactory,
) {
    val credentials: Flow<SeerrCredentials?> get() = store.credentials

    private val userLock = Mutex()
    private var cachedUser: Pair<SeerrCredentials, SeerrUserDto>? = null

    /** The API for the saved server, or [NotConnectedException] when nothing is saved. */
    suspend fun api(): SeerrApi {
        val saved = store.credentials.first() ?: throw NotConnectedException()
        return apis.cached(saved.baseUrl, saved.auth)
    }

    /** The saved credentials, or [NotConnectedException]. */
    suspend fun current(): SeerrCredentials = store.credentials.first() ?: throw NotConnectedException()

    /**
     * The authenticated user for the saved connection, served from cache while the credentials are
     * unchanged. A failed fetch is not cached, so a transient `auth/me` failure cannot pin "no
     * permissions" for the rest of the connection.
     */
    suspend fun authenticatedUser(): SeerrUserDto {
        val saved = current()
        return userLock.withLock {
            cachedUser?.takeIf { it.first == saved }?.second
                ?: apis.cached(saved.baseUrl, saved.auth).authenticatedUser().also { cachedUser = saved to it }
        }
    }

    /**
     * Validates [rawBaseUrl] + [auth] against the live server and persists them only if the probe
     * succeeds, so a stored connection always worked at least once. The URL is normalised before
     * both the probe and the save, so what was validated is exactly what is stored.
     */
    suspend fun connect(
        rawBaseUrl: String,
        auth: SeerrAuth,
    ): Result<SeerrCredentials> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return runCatching { apis.probe(baseUrl, auth) { it.authenticatedUser() } }
            .mapCatching { persist(baseUrl, auth) }
    }

    /** Logs in as a user and, on success, persists the session so the connection runs as that user. */
    suspend fun logIn(
        rawBaseUrl: String,
        request: SeerrLoginRequest,
    ): Result<SeerrCredentials> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return runCatching {
            val result =
                apis.login(baseUrl) { api ->
                    when (request) {
                        is SeerrLoginRequest.Jellyfin -> api.logInWithJellyfin(SeerrJellyfinLoginBody(request.username, request.password))
                        is SeerrLoginRequest.Local -> api.logInWithLocal(SeerrLocalLoginBody(request.email, request.password))
                    }
                }
            val cookie = result.sessionCookie ?: throw NoSessionCookieException()
            persist(baseUrl, SeerrAuth.Session(cookie = cookie, userId = result.value.id))
        }
    }

    suspend fun disconnect() {
        userLock.withLock { cachedUser = null }
        store.clear()
    }

    /**
     * Best-effort fork detection rides along: the provider name the host shows follows the server
     * the user actually connected to, and an unreachable `/status` brands neutrally rather than
     * failing a connect that just succeeded.
     */
    private suspend fun persist(
        baseUrl: String,
        auth: SeerrAuth,
    ): SeerrCredentials {
        val variant =
            runCatching { apis.probe(baseUrl, auth) { it.status().version } }
                .map(SeerrVariant::fromVersion)
                .getOrDefault(SeerrVariant.Unknown)
        val credentials = SeerrCredentials(baseUrl, auth, variant)
        if (!store.save(credentials)) throw CredentialsSaveException()
        userLock.withLock { cachedUser = null }
        return credentials
    }
}
