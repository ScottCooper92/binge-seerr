package com.cooper.binge.companion.seerr.auth

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.demo.isDemo
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrJellyfinLoginBody
import com.cooper.binge.companion.seerr.seerr.SeerrLocalLoginBody
import com.cooper.binge.companion.seerr.seerr.SeerrLoginRequest
import com.cooper.binge.companion.seerr.seerr.SeerrPasswordResetBody
import com.cooper.binge.companion.seerr.seerr.SeerrPlexLoginBody
import com.cooper.binge.companion.seerr.seerr.SeerrQuickConnectSecretBody
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.insecurePublicHostOrNull
import com.cooper.binge.companion.seerr.seerr.inspectProfile
import com.cooper.binge.companion.seerr.seerr.isValidBaseUrl
import com.cooper.binge.companion.seerr.seerr.normaliseBaseUrl
import com.cooper.binge.companion.seerr.seerr.readProfile
import com.cooper.binge.companion.seerr.seerr.rejectsSession
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    /** The off-device copy, kept in step with the store so a new device has something to restore from. */
    private val carrier: ConnectionCarrier = NoConnectionCarrier,
    /** Runs after the saved server changes or is forgotten, for the caches keyed to one server. */
    private val onServerChanged: suspend () -> Unit = {},
    /** Public hosts agreed to over plain HTTP. Narrowed to the saved server whenever it changes. */
    private val cleartext: CleartextConsent = CleartextConsent.None,
) {
    private val profileReads = ProfileReads()

    val credentials: Flow<SeerrCredentials?> get() = store.credentials

    /** The server the user signed out of, while nothing is saved; see [signOut]. */
    val signedOutServer: Flow<String?> get() = store.signedOutServer

    /**
     * Live health of the saved server, fed by every call the cached client makes.
     *
     * [SeerrConnectionHealth.NotConnected] is answered from the store, not the monitor: the monitor
     * reports what calls said and cannot know whether anything is saved. So with nothing saved this
     * is NotConnected whatever the last call before the disconnect reported, and with something
     * saved but not yet called it is [SeerrConnectionHealth.Unchecked] rather than a claim that no
     * connection exists.
     */
    val health: Flow<SeerrConnectionHealth> =
        combine(store.credentials, healthMonitor.health) { saved, reported ->
            if (saved == null) SeerrConnectionHealth.NotConnected else reported
        }.distinctUntilChanged()

    /**
     * Whether the server has rejected the saved session (#810), which moves the app off its connected screens and onto
     * sign-in. [health] flips on any one rejected call and back on the next success, so a rejection is believed only
     * once a fresh `auth/me` agrees: a stray 401 from a proxy does not throw a signed-in user out. An `auth/me` that
     * cannot be read at all is no answer, so the user stays where they are.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionRejected: Flow<Boolean> =
        health
            .map { it == SeerrConnectionHealth.Unauthorized }
            .distinctUntilChanged()
            .mapLatest { flagged ->
                flagged && attempt { refreshAuthenticatedUser() }.exceptionOrNull()?.toSeerrError()?.rejectsSession == true
            }.distinctUntilChanged()

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
     * its latest, so a transient failure hides nothing; that guess is not cached, so the next call
     * reads again. [refreshProfile] re-reads after an upgrade.
     */
    suspend fun profile(): SeerrServerProfile {
        val saved = current()
        val seen = profileReads.failures
        return userLock.withLock {
            cachedProfile?.takeIf { it.first == saved }?.second
                ?: profileReads
                    .inspect(saved, apis.cached(saved.baseUrl, saved.auth), seen)
                    .getOrElse { SeerrServerProfile.unknown(saved.variant) }
                    .also { if (it.complete) cachedProfile = saved to it }
        }
    }

    /**
     * Re-reads `auth/me` rather than serving the cached copy. The cache is per connection, so a
     * permission granted or revoked on the server is otherwise invisible until the app reconnects;
     * a screen that re-resolves its scope on entry has to ask for this explicitly.
     */
    suspend fun refreshAuthenticatedUser(): SeerrUserDto {
        userLock.withLock { cachedUser = null }
        return authenticatedUser()
    }

    suspend fun refreshProfile(): SeerrServerProfile {
        userLock.withLock { cachedProfile = null }
        return profile()
    }

    /**
     * [refreshProfile] that says when the server answered neither profile call, rather than
     * profiling it as a guess. A server that answered either one is a success, however partial.
     */
    suspend fun refreshProfileOrFail(): Result<SeerrServerProfile> {
        val saved = current()
        val seen = profileReads.failures
        return userLock.withLock {
            cachedProfile = null
            profileReads.inspect(saved, apis.cached(saved.baseUrl, saved.auth), seen).onSuccess {
                if (it.complete) {
                    cachedProfile =
                        saved to it
                }
            }
        }
    }

    /**
     * What [rawBaseUrl] is, before any credential: its profile and, best-effort, its artwork. Fails
     * with the `/status` error when the address answers neither profile call, so the form can say
     * "unreachable" before asking for a password it would only reject.
     *
     * A portless address is retried at `:5055` ([withPortFallback]) on either failure kind, since
     * every fork's Docker image defaults there and a bare host otherwise resolves to port 80.
     * [SeerrServerPreview.baseUrl] is whichever candidate answered.
     */
    suspend fun inspect(rawBaseUrl: String): Result<SeerrServerPreview> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return withPortFallback(baseUrl, ::inspectCandidate)
    }

    /**
     * Probes one candidate base URL, anonymous, for its profile and artwork. On success
     * [SeerrServerPreview.baseUrl] is exactly [baseUrl] — the candidate that answered — so
     * [withPortFallback]'s retry carries forward whichever address actually worked.
     */
    private suspend fun inspectCandidate(baseUrl: String): Result<SeerrServerPreview> =
        attempt {
            apis.anonymous(baseUrl) { api ->
                val profile =
                    api.inspectProfile(SeerrVariant.Unknown).getOrElse { failure ->
                        throw if (failure is IOException) failure else NotSeerrServerException(failure)
                    }
                val backdrops = attempt { api.backdrops() }.getOrDefault(emptyList())
                SeerrServerPreview(baseUrl, profile, backdrops.map { it.toTmdbBackdropUrl() })
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
        return attempt { apis.probe(baseUrl, auth) { it.authenticatedUser() } }
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

    /**
     * Keeps a session another device signed in with (#772): the phone's `connect.sid`, handed to a television. It is
     * checked against `auth/me` on [rawBaseUrl] first, and kept only if the server answers as a user, so an expired
     * or foreign cookie is discarded rather than saved. The user id is the server's answer, not the sender's.
     */
    suspend fun adoptSession(
        rawBaseUrl: String,
        cookie: String,
    ): Result<SeerrCredentials> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return attempt {
            val user = apis.probe(baseUrl, SeerrAuth.Session(cookie = cookie, userId = 0)) { it.authenticatedUser() }
            persist(baseUrl, SeerrAuth.Session(cookie = cookie, userId = user.id, shared = true))
        }
    }

    /**
     * Marks the saved session as shared with another device (#772), once this phone has sent it to a TV: from then on
     * disconnecting here forgets it without ending it on the server, so the TV isn't signed out with it.
     */
    suspend fun markSessionShared() {
        val saved = store.credentials.first() ?: return
        val session = saved.auth as? SeerrAuth.Session ?: return
        if (session.shared) return
        val shared = saved.copy(auth = session.copy(shared = true))
        // The carried copy too, or a device restored from it would end the session on disconnect (#811).
        if (store.save(shared) && !shared.isDemo) carrier.put(cleartext.carriedFor(shared))
    }

    /** Starts a Quick Connect session on the server's Jellyfin; the code is for the user, the secret for [finishQuickConnect]. */
    suspend fun startQuickConnect(rawBaseUrl: String): Result<SeerrQuickConnect> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return attempt { apis.anonymous(baseUrl) { api -> api.initiateQuickConnect() } }
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
        return awaitQuickConnect(baseUrl, session).mapCatching {
            logInForSession(baseUrl) { api -> api.authenticateQuickConnect(SeerrQuickConnectSecretBody(session.secret)) }.getOrThrow()
        }
    }

    /**
     * Polls until the user approves the code on Jellyfin, and nothing more: the caller decides
     * whether the approval signs in ([finishQuickConnect]) or links the account to the signed-in
     * user. A 404 from the check is Jellyfin having expired the code, [QuickConnectExpiredException].
     */
    suspend fun awaitQuickConnect(
        rawBaseUrl: String,
        session: SeerrQuickConnect,
    ): Result<Unit> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return attempt {
            apis.anonymous(baseUrl) { api ->
                while (!api.checkQuickConnectApproved(session.secret)) delay(quickConnectPollInterval)
            }
        }
    }

    /** Whether the user has opted in to plain HTTP to the public [host]. */
    suspend fun allowsCleartextTo(host: String): Boolean = cleartext.allows(host)

    /**
     * Records the user's opt-in to plain HTTP to the public [host], ahead of reading it. It is dropped
     * the next time a server is saved or forgotten, so an abandoned setup leaves it until then.
     */
    suspend fun allowCleartextTo(host: String) = cleartext.grant(host)

    /** Asks the server to email a reset link; it answers 200 whether or not the address is an account's. */
    suspend fun requestPasswordReset(
        rawBaseUrl: String,
        email: String,
    ): Result<Unit> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return attempt { apis.anonymous(baseUrl) { api -> api.requestPasswordReset(SeerrPasswordResetBody(email)) } }
    }

    /**
     * Forgets the connection. A session sign-in is also ended on the server, best-effort: the local
     * clear must not wait on a server that may be the reason the user is disconnecting. A session shared with
     * another device is only forgotten here: ending it would sign that device out too.
     */
    suspend fun disconnect() = forget(keepAddress = false)

    /**
     * Ends this session as [disconnect] does, but keeps the server's address as [signedOutServer], so setup opens on that
     * server's sign-in: someone signing out of a shared television means to sign in again, not to set up a new server.
     */
    suspend fun signOut() = forget(keepAddress = true)

    private suspend fun forget(keepAddress: Boolean) {
        val saved = store.credentials.first()
        val auth = saved?.auth
        if (auth is SeerrAuth.Session && !auth.shared) attempt { apis.cached(saved.baseUrl, auth).logOut() }
        userLock.withLock {
            cachedUser = null
            cachedProfile = null
        }
        // The demo has no sign-in to return to, so signing out of it is leaving it.
        if (keepAddress && saved?.isDemo != true) store.signOut() else store.clear()
        cleartext.retainOnly(null)
        carrier.clear()
        apis.evict()
        apis.resetDemo()
        healthMonitor.reset()
        onServerChanged()
    }

    /** The shared tail of every sign-in that ends in a session: run [block] on the capturing client, keep the cookie. */
    private suspend fun logInForSession(
        rawBaseUrl: String,
        block: suspend (SeerrApi) -> SeerrUserDto,
    ): Result<SeerrCredentials> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return attempt {
            val result = apis.login(baseUrl, block)
            val cookie = result.sessionCookie ?: throw NoSessionCookieException()
            persist(baseUrl, SeerrAuth.Session(cookie = cookie, userId = result.value.id))
        }
    }

    /**
     * Best-effort fork detection rides along: the provider name the host shows follows the server
     * the user actually connected to, and an unreachable `/status` brands neutrally rather than
     * failing a connect that just succeeded. The profile read here is cached for the connection,
     * unless it was a guess.
     */
    private suspend fun persist(
        baseUrl: String,
        auth: SeerrAuth,
    ): SeerrCredentials {
        val profile = apis.probe(baseUrl, auth) { it.readProfile(SeerrVariant.Unknown) }
        val credentials = SeerrCredentials(baseUrl, auth, profile.variant)
        if (!store.save(credentials)) throw CredentialsSaveException()
        // Consent is for plain HTTP to this server, so it survives only when the saved address is itself plain HTTP to a
        // public host. Moving the same host to https drops it, and a redirect back down to http is refused (#1034).
        cleartext.retainOnly(baseUrl.insecurePublicHostOrNull())
        // A public plain-HTTP server is only reachable with the opt-in, so a new device needs it too.
        // The demo has nothing to restore on another device: it is the same canned data everywhere.
        if (!credentials.isDemo) carrier.put(cleartext.carriedFor(credentials))
        userLock.withLock {
            cachedUser = null
            cachedProfile = (credentials to profile).takeIf { profile.complete }
        }
        healthMonitor.onConnected()
        onServerChanged()
        return credentials
    }
}
