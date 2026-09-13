package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrJellyfinLoginBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrLocalLoginBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrLoginRequest
import io.github.scottcooper92.binge.seerr.seerr.SeerrPasswordResetBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrPlexLoginBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrQuickConnectSecretBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.inspectProfile
import io.github.scottcooper92.binge.seerr.seerr.isValidBaseUrl
import io.github.scottcooper92.binge.seerr.seerr.normaliseBaseUrl
import io.github.scottcooper92.binge.seerr.seerr.readProfile
import io.github.scottcooper92.binge.seerr.seerr.toTmdbBackdropUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val HTTP_NOT_FOUND = 404

/** The entered address cannot be parsed as a URL even after normalisation. */
class InvalidServerUrlException : IllegalArgumentException("Invalid Seerr server URL")

/** At-rest encryption of the credentials failed, so nothing was persisted. */
class CredentialsSaveException : IllegalStateException("Failed to store the Seerr credentials")

/** The login succeeded but the server set no session cookie, so there is nothing to keep. */
class NoSessionCookieException : IllegalStateException("Seerr login returned no session cookie")

/** No server is connected: the code the contract's `UNAUTHENTICATED` is mapped from. */
class NotConnectedException : IllegalStateException("No Seerr server is connected")

/** The address answered, but not as a Seerr server: neither profile call came back as one. */
class NotSeerrServerException(
    cause: Throwable,
) : IllegalStateException("The address did not answer as a Seerr server", cause)

/** The Quick Connect code was not approved on the Jellyfin server before it expired. */
class QuickConnectExpiredException : IllegalStateException("The Quick Connect code expired before it was approved")

/** What an address answered before any credential was typed: the sign-in form is built from this. */
data class SeerrServerPreview(
    val baseUrl: String,
    val profile: SeerrServerProfile,
    val backdropUrls: List<String>,
)

/** A Quick Connect session: the [code] the user approves on Jellyfin, the [secret] this app polls with. */
data class SeerrQuickConnect(
    val code: String,
    val secret: String,
)

/**
 * The one connected server: connecting, logging in, disconnecting, and the API for the saved
 * credentials. The authenticated user (`auth/me`) is cached per credentials so the capability
 * handshake and the permission checks share one lookup per connection rather than one per call.
 */
class SeerrConnection(
    private val store: CredentialStore,
    private val apis: SeerrApiFactory,
    private val healthMonitor: SeerrConnectionHealthMonitor = SeerrConnectionHealthMonitor(),
    private val quickConnectPollInterval: Duration = 2.seconds,
    /** Runs after the saved server changes or is forgotten, for the caches keyed to one server. */
    private val onServerChanged: suspend () -> Unit = {},
) {
    val credentials: Flow<SeerrCredentials?> get() = store.credentials

    /** Live health of the saved server, fed by every call the cached client makes. */
    val health: StateFlow<SeerrConnectionHealth> get() = healthMonitor.health

    private val userLock = Mutex()
    private var cachedUser: Pair<SeerrCredentials, SeerrUserDto>? = null
    private var cachedProfile: Pair<SeerrCredentials, SeerrServerProfile>? = null

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
     * What the saved server is and can do, served from cache while the credentials are unchanged.
     * A server that answers neither call is profiled as the lineage the credentials recorded, at
     * its latest, so a transient failure hides nothing; [refreshProfile] re-reads after an upgrade.
     */
    suspend fun profile(): SeerrServerProfile {
        val saved = current()
        return userLock.withLock {
            cachedProfile?.takeIf { it.first == saved }?.second
                ?: apis.cached(saved.baseUrl, saved.auth).readProfile(saved.variant).also { cachedProfile = saved to it }
        }
    }

    suspend fun refreshProfile(): SeerrServerProfile {
        userLock.withLock { cachedProfile = null }
        return profile()
    }

    /**
     * What [rawBaseUrl] is, before any credential: its profile and, best-effort, its artwork. Fails
     * with the `/status` error when the address answers neither profile call, so the form can say
     * "unreachable" before asking for a password it would only reject.
     */
    suspend fun inspect(rawBaseUrl: String): Result<SeerrServerPreview> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return runCatching {
            apis.anonymous(baseUrl) { api ->
                val profile =
                    api.inspectProfile(SeerrVariant.Unknown).getOrElse { failure ->
                        throw if (failure is IOException) failure else NotSeerrServerException(failure)
                    }
                val backdrops = runCatching { api.backdrops() }.getOrDefault(emptyList())
                SeerrServerPreview(baseUrl, profile, backdrops.map { it.toTmdbBackdropUrl() })
            }
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
    ): Result<SeerrCredentials> =
        logInForSession(rawBaseUrl) { api ->
            when (request) {
                is SeerrLoginRequest.Jellyfin -> api.logInWithJellyfin(SeerrJellyfinLoginBody(request.username, request.password))
                is SeerrLoginRequest.Local -> api.logInWithLocal(SeerrLocalLoginBody(request.email, request.password))
            }
        }

    /** Signs in with a plex.tv token from the PIN flow ([PlexPinFlow]) and keeps the session. */
    suspend fun logInWithPlex(
        rawBaseUrl: String,
        authToken: String,
    ): Result<SeerrCredentials> = logInForSession(rawBaseUrl) { api -> api.logInWithPlex(SeerrPlexLoginBody(authToken)) }

    /** Starts a Quick Connect session on the server's Jellyfin; the code is for the user, the secret for [finishQuickConnect]. */
    suspend fun startQuickConnect(rawBaseUrl: String): Result<SeerrQuickConnect> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return runCatching { apis.anonymous(baseUrl) { api -> api.initiateQuickConnect() } }
            .map { SeerrQuickConnect(code = it.code, secret = it.secret) }
    }

    /**
     * Polls until the user approves the code on Jellyfin, then signs in as them and keeps the
     * session. A 404 from the check is Jellyfin having expired the code, [QuickConnectExpiredException].
     */
    suspend fun finishQuickConnect(
        rawBaseUrl: String,
        session: SeerrQuickConnect,
    ): Result<SeerrCredentials> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return try {
            apis.anonymous(baseUrl) { api ->
                while (!api.checkQuickConnectApproved(session.secret)) delay(quickConnectPollInterval)
            }
            logInForSession(baseUrl) { api -> api.authenticateQuickConnect(SeerrQuickConnectSecretBody(session.secret)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Asks the server to email a reset link; it answers 200 whether or not the address is an account's. */
    suspend fun requestPasswordReset(
        rawBaseUrl: String,
        email: String,
    ): Result<Unit> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return runCatching { apis.anonymous(baseUrl) { api -> api.requestPasswordReset(SeerrPasswordResetBody(email)) } }
    }

    /**
     * Forgets the connection. A session sign-in is also ended on the server, best-effort: the local
     * clear must not wait on a server that may be the reason the user is disconnecting.
     */
    suspend fun disconnect() {
        val saved = store.credentials.first()
        if (saved?.auth is SeerrAuth.Session) runCatching { apis.cached(saved.baseUrl, saved.auth).logOut() }
        userLock.withLock {
            cachedUser = null
            cachedProfile = null
        }
        store.clear()
        apis.evict()
        healthMonitor.reset()
        onServerChanged()
    }

    private suspend fun SeerrApi.checkQuickConnectApproved(secret: String): Boolean =
        try {
            checkQuickConnect(secret).authenticated
        } catch (e: HttpException) {
            if (e.code() == HTTP_NOT_FOUND) throw QuickConnectExpiredException() else throw e
        }

    /** The shared tail of every sign-in that ends in a session: run [block] on the capturing client, keep the cookie. */
    private suspend fun logInForSession(
        rawBaseUrl: String,
        block: suspend (SeerrApi) -> SeerrUserDto,
    ): Result<SeerrCredentials> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return runCatching {
            val result = apis.login(baseUrl, block)
            val cookie = result.sessionCookie ?: throw NoSessionCookieException()
            persist(baseUrl, SeerrAuth.Session(cookie = cookie, userId = result.value.id))
        }
    }

    /**
     * Best-effort fork detection rides along: the provider name the host shows follows the server
     * the user actually connected to, and an unreachable `/status` brands neutrally rather than
     * failing a connect that just succeeded. The profile read here is cached for the connection.
     */
    private suspend fun persist(
        baseUrl: String,
        auth: SeerrAuth,
    ): SeerrCredentials {
        val profile = apis.probe(baseUrl, auth) { it.readProfile(SeerrVariant.Unknown) }
        val credentials = SeerrCredentials(baseUrl, auth, profile.variant)
        if (!store.save(credentials)) throw CredentialsSaveException()
        userLock.withLock {
            cachedUser = null
            cachedProfile = credentials to profile
        }
        healthMonitor.onConnected()
        onServerChanged()
        return credentials
    }
}
