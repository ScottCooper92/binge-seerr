package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import kotlinx.coroutines.test.TestScope
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal const val ADMIN = 2
internal const val MANAGE_USERS = 1 shl 3
internal const val MANAGE_REQUESTS = 1 shl 4
internal const val REQUEST = 1 shl 5

/**
 * A path-scripted Seerr for the settings pages: each `METHOD path` answers with the body last
 * served for it, so a test can switch a record after a write. Main is set once per test and
 * never reset, as in every other ViewModel test here.
 */
internal class ScriptedSeerr(
    private val folder: TemporaryFolder,
) {
    val server = MockWebServer()
    val received = CopyOnWriteArrayList<RecordedRequest>()
    private val responses = mutableMapOf<String, () -> MockResponse>()
    private var stores = 0

    fun start() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return responses[request.method + " " + request.url.encodedPath]?.invoke() ?: MockResponse(code = 404)
                }
            }
        server.start()
    }

    fun close() = server.close()

    fun serve(
        key: String,
        body: String = "{}",
        code: Int = 200,
    ) {
        responses[key] = { MockResponse(code = code, headers = headersOf("Content-Type", "application/json"), body = body) }
    }

    fun remove(key: String) {
        responses.remove(key)
    }

    /** The viewer and the server every page reads first. */
    fun viewer(
        id: Int,
        permissions: Int,
        version: String = "3.4.0",
        settings: String = """{"mediaServerType":2,"localLogin":true}""",
    ) {
        serve("GET /api/v1/auth/me", """{"id":$id,"displayName":"Viewer","permissions":$permissions}""")
        serve("GET /api/v1/status", """{"version":"$version"}""")
        serve("GET /api/v1/settings/public", settings)
    }

    fun body(
        method: String,
        path: String,
    ): String =
        received
            .last { it.method == method && it.url.encodedPath == path }
            .body
            ?.utf8()
            .orEmpty()

    fun count(
        method: String,
        path: String,
    ): Int = received.count { it.method == method && it.url.encodedPath == path }

    suspend fun connection(
        scope: TestScope,
        quickConnectPollInterval: Duration = 10.milliseconds,
        onServerChanged: suspend () -> Unit = {},
    ): SeerrConnection {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.newFile("d${stores++}.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false),
                quickConnectPollInterval = quickConnectPollInterval,
                onServerChanged = onServerChanged,
            )
        connection.connect(server.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        return connection
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
