package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthReporter
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
) {
    /** `explicitNulls = false` so an omitted field (`seasons` on a movie request) is dropped from the body, not sent as null. */
    private val json =
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
                        .addInterceptor(SeerrHealthInterceptor(health))
                        .applyAuth(auth, baseUrl)
                        .finish(HttpLoggingInterceptor.Level.BODY)
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
        val client = OkHttpClient.Builder().applyAuth(auth, baseUrl).finish(HttpLoggingInterceptor.Level.BODY)
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
        val client = OkHttpClient.Builder().cookieJar(capture).finish(HttpLoggingInterceptor.Level.HEADERS)
        return try {
            SeerrLoginResult(value = block(retrofit(baseUrl, client)), sessionCookie = capture.sessionCookie)
        } finally {
            client.release()
        }
    }

    private fun OkHttpClient.Builder.finish(debugLevel: HttpLoggingInterceptor.Level): OkHttpClient =
        addNetworkInterceptor(loggingInterceptor(debugLevel))
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
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

    /** An `X-Api-Key` header, or a host-scoped jar pre-loaded with the session cookie. */
    private fun OkHttpClient.Builder.applyAuth(
        auth: SeerrAuth,
        baseUrl: String,
    ): OkHttpClient.Builder =
        when (auth) {
            is SeerrAuth.ApiKey ->
                addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .addHeader(API_KEY_HEADER, auth.key)
                            .build(),
                    )
                }
            is SeerrAuth.Session -> cookieJar(SessionCookieJar(baseUrl, auth.cookie))
        }

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
        const val TIMEOUT_SECONDS = 15L
        val REDACTED_HEADERS = listOf(API_KEY_HEADER, "Authorization", "Cookie", "Set-Cookie")
    }
}

/** The result of a [SeerrApiFactory.login]: the call's [value] plus the captured cookie, if the server set one. */
data class SeerrLoginResult<T>(
    val value: T,
    val sessionCookie: String?,
)

private const val SESSION_COOKIE_NAME = "connect.sid"

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
