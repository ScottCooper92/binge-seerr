package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.CleartextConsent
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthReporter
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds a [SeerrApi] for a user-supplied server. The base URL is only known once the user has
 * connected, so Retrofit is built per server, with that server's own credentials and nothing else.
 *
 * Two paths: [cached] serves the one saved server and reuses a connection pool across calls;
 * [probe] and [login] build a throwaway client for candidate credentials during setup, released as
 * soon as the call returns, so an unvalidated server never displaces the cache.
 */
class SeerrApiFactory(
    private val logRequests: Boolean,
    /** Fed by the cached client only: a probe's candidate credentials never speak for the saved server. */
    private val health: SeerrConnectionHealthReporter = SeerrConnectionHealthReporter.NoOp,
    /** Called on the cached client only, whenever the saved server accepts a write. */
    private val onWrite: () -> Unit = {},
    /** The public hosts the user agreed to reach over plain HTTP. Every client this factory builds enforces it. */
    private val cleartext: CleartextConsent = CleartextConsent.None,
    /**
     * No socket exists when this is set: every client this factory builds is answered by the
     * interceptor it returns for that call's cookie jar, instead of the network. Test-only (#337).
     */
    internal val testTransport: ((CookieJar) -> Interceptor)? = null,
    /**
     * A fresh [Dispatcher] for every client this factory builds, when set. Test-only (#177): it
     * is how a test drains OkHttp's own thread before resetting `Dispatchers.Main` - see
     * `FakeSeerrServer.awaitIdle`.
     */
    internal val testDispatcher: (() -> Dispatcher)? = null,
) {
    /**
     * `explicitNulls = false` so an omitted field (`seasons` on a movie request) is dropped from the body, not sent as null.
     * Internal so a decoding test reads responses with the client's own configuration.
     */
    internal val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private var cached: CachedApi? = null

    /** The service for the saved server, rebuilt only when the credentials change. */
    fun cached(
        baseUrl: String,
        auth: SeerrAuth,
    ): SeerrApi =
        synchronized(this) {
            val current = cached
            if (current != null && current.baseUrl == baseUrl && current.auth == auth) {
                current.api
            } else {
                current?.client?.release()
                val client =
                    OkHttpClient
                        .Builder()
                        .addInterceptor(SeerrHealthInterceptor(health, baseUrl))
                        .addInterceptor(SeerrWriteInterceptor(onWrite))
                        .addInterceptor(SeerrSessionInterceptor(baseUrl))
                        .applyAuth(auth, baseUrl)
                        .finish(HttpLoggingInterceptor.Level.BODY, sessionCookieJarOrNull(auth, baseUrl))
                CachedApi(baseUrl, auth, client, retrofit(baseUrl, client)).also { cached = it }.api
            }
        }

    /**
     * Drops the cached service and releases its pool. Disconnecting clears the credentials, and a
     * pool held for a server the user has left is a pool held for nothing.
     */
    fun evict() {
        synchronized(this) {
            cached?.client?.release()
            cached = null
        }
    }

    /** A throwaway service for probing candidate credentials, released when [block] returns. */
    suspend fun <T> probe(
        baseUrl: String,
        auth: SeerrAuth,
        block: suspend (SeerrApi) -> T,
    ): T {
        val client =
            OkHttpClient.Builder().applyAuth(auth, baseUrl).finish(HttpLoggingInterceptor.Level.BODY, sessionCookieJarOrNull(auth, baseUrl))
        return try {
            block(retrofit(baseUrl, client))
        } finally {
            client.release()
        }
    }

    /**
     * Runs a login [block] against a throwaway, auth-less client whose cookie jar records the
     * `connect.sid` the login response sets. HEADERS logging, never BODY: the login body carries the
     * password, and redaction covers headers only.
     */
    suspend fun <T> login(
        baseUrl: String,
        block: suspend (SeerrApi) -> T,
    ): SeerrLoginResult<T> {
        val capture = CapturingCookieJar()
        val client = OkHttpClient.Builder().cookieJar(capture).finish(HttpLoggingInterceptor.Level.HEADERS, capture)
        return try {
            SeerrLoginResult(value = block(retrofit(baseUrl, client)), sessionCookie = capture.sessionCookie)
        } finally {
            client.release()
        }
    }

    /**
     * A throwaway client with no credentials at all, for the calls a server answers before
     * sign-in: its profile, its artwork, a Quick Connect code, a password reset. HEADERS logging
     * for the same reason as [login]: a reset body carries the address.
     */
    suspend fun <T> anonymous(
        baseUrl: String,
        block: suspend (SeerrApi) -> T,
    ): T {
        val client = OkHttpClient.Builder().finish(HttpLoggingInterceptor.Level.HEADERS, cookieJar = null)
        return try {
            block(retrofit(baseUrl, client))
        } finally {
            client.release()
        }
    }

    /**
     * The pool's idle timeout is the load-bearing setting here, not a tuning knob. Seerr servers
     * advertise `Keep-Alive: timeout=5` and hang up an idle connection after it; OkHttp's default
     * pool holds one for five minutes. Retiring ours first means a request is never written onto a
     * connection the server is about to close — which OkHttp does not recover from, because its
     * retry looks for another route and a single server offers none (#254).
     */
    private fun OkHttpClient.Builder.finish(
        debugLevel: HttpLoggingInterceptor.Level,
        cookieJar: CookieJar?,
    ): OkHttpClient =
        apply { testTransport?.let { addInterceptor(it(cookieJar ?: CookieJar.NO_COOKIES)) } }
            .apply { testDispatcher?.let { dispatcher(it()) } }
            .addNetworkInterceptor(CleartextGuard(cleartext))
            .addNetworkInterceptor(loggingInterceptor(debugLevel))
            .connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /**
     * A NETWORK interceptor so it sees the fully-formed request — including the `Cookie` header the
     * jar attaches downstream of application interceptors — and can redact it. Every secret here is
     * header-borne, so redacting [REDACTED_HEADERS] masks all of them.
     */
    private fun loggingInterceptor(debugLevel: HttpLoggingInterceptor.Level): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (logRequests) debugLevel else HttpLoggingInterceptor.Level.NONE
            REDACTED_HEADERS.forEach(::redactHeader)
        }

    private fun retrofit(
        baseUrl: String,
        client: OkHttpClient,
    ): SeerrApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(CONTENT_TYPE.toMediaType()))
            .build()
            .create(SeerrApi::class.java)

    /**
     * An `X-Api-Key` header sent only to the saved server's own origin, or a host-scoped jar
     * pre-loaded with the session cookie.
     *
     * The key is added per hop, in a **network** interceptor: an application interceptor's request is
     * the one OkHttp copies onto a redirect's next hop, which would carry the key to whatever host,
     * or plain-http URL, the server or a proxy in front of it named. A saved URL that does not parse
     * sends no key at all.
     */
    private fun OkHttpClient.Builder.applyAuth(
        auth: SeerrAuth,
        baseUrl: String,
    ): OkHttpClient.Builder =
        when (auth) {
            is SeerrAuth.ApiKey -> {
                val origin = baseUrl.toHttpUrlOrNull()
                addNetworkInterceptor { chain ->
                    val request = chain.request()
                    val toSavedServer = origin != null && request.url.sharesOriginWith(origin)
                    chain.proceed(if (toSavedServer) request.newBuilder().addHeader(API_KEY_HEADER, auth.key).build() else request)
                }
            }
            is SeerrAuth.Session -> cookieJar(sessionCookieJarOrNull(auth, baseUrl) ?: CookieJar.NO_COOKIES)
        }

    /** The same jar [applyAuth] sets for [SeerrAuth.Session], read back for [finish] to hand a test transport. */
    private fun sessionCookieJarOrNull(
        auth: SeerrAuth,
        baseUrl: String,
    ): CookieJar? = (auth as? SeerrAuth.Session)?.let { SessionCookieJar(baseUrl, it.cookie) }

    private fun OkHttpClient.release() {
        dispatcher.executorService.shutdown()
        connectionPool.evictAll()
    }

    private class CachedApi(
        val baseUrl: String,
        val auth: SeerrAuth,
        val client: OkHttpClient,
        val api: SeerrApi,
    )

    private companion object {
        const val API_KEY_HEADER = "X-Api-Key"
        const val CONTENT_TYPE = "application/json; charset=UTF-8"
        const val MAX_IDLE_CONNECTIONS = 5
        const val IDLE_TIMEOUT_SECONDS = 3L
        val REDACTED_HEADERS = listOf(API_KEY_HEADER, "Authorization", "Cookie", "Set-Cookie")
    }
}

/**
 * How long OkHttp waits for the TCP socket to the server to connect, per address attempt. A server on
 * a LAN, VPN or the internet accepts in milliseconds, so a dark host (Tailscale off) fails fast. It
 * covers the connect only, not the TLS handshake or the response, which [READ_TIMEOUT_SECONDS] bounds.
 */
internal const val CONNECT_TIMEOUT_SECONDS = 5L

/** How long a socket read may stall, which also bounds the TLS handshake. Slow servers need this long. */
internal const val READ_TIMEOUT_SECONDS = 15L

/** The result of a [SeerrApiFactory.login]: the call's [value] plus the captured cookie, if the server set one. */
data class SeerrLoginResult<T>(
    val value: T,
    val sessionCookie: String?,
)

private const val SESSION_COOKIE_NAME = "connect.sid"

/**
 * True when this request is to the saved server's own origin: same scheme, host and port. That is the
 * only place the API key may go, so an https-to-http downgrade and a hop to another host or port both
 * fail it.
 */
internal fun HttpUrl.sharesOriginWith(base: HttpUrl): Boolean = scheme == base.scheme && host == base.host && port == base.port

/**
 * Replays one `connect.sid` cookie, scoped to the saved server's host. [loadForRequest] filters on
 * [Cookie.matches], so a cross-host redirect never receives the session.
 */
internal class SessionCookieJar(
    baseUrl: String,
    cookieValue: String,
) : CookieJar {
    private val cookies: List<Cookie> =
        baseUrl
            .toHttpUrlOrNull()
            ?.let { url ->
                listOf(
                    Cookie
                        .Builder()
                        .name(SESSION_COOKIE_NAME)
                        .value(cookieValue)
                        .hostOnlyDomain(url.host)
                        .path("/")
                        .build(),
                )
            }.orEmpty()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) = Unit

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
}

/** Records the `connect.sid` a login response sets and sends nothing on requests. */
private class CapturingCookieJar : CookieJar {
    @Volatile
    var sessionCookie: String? = null
        private set

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        cookies.firstOrNull { it.name == SESSION_COOKIE_NAME }?.let { sessionCookie = it.value }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
}
