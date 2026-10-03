package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.CleartextConsent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The guard on its own, ahead of a canned answer instead of a socket. In the factory it is a network
 * interceptor, which a test transport answers before; here it is the only thing between the call and
 * the answer, so what it lets through is exactly what reaches [answered].
 */
class CleartextGuardTest {
    private val answered = mutableListOf<String>()

    private fun client(consent: CleartextConsent): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(CleartextGuard(consent))
            .addInterceptor(
                Interceptor { chain ->
                    answered += chain.request().url.toString()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody())
                        .build()
                },
            ).build()

    private fun OkHttpClient.get(url: String) = newCall(Request.Builder().url(url).build()).execute().close()

    private class Granted(
        private val host: String,
    ) : CleartextConsent {
        override suspend fun allows(host: String) = host == this.host

        override suspend fun grant(host: String) = Unit

        override suspend fun retainOnly(host: String?) = Unit
    }

    @Test
    fun `plain http to a public host is refused before it is sent`() {
        val refused =
            assertThrows(
                CleartextRefusedException::class.java,
            ) { client(CleartextConsent.None).get("http://seerr.example.com/api/v1/status") }

        assertEquals("seerr.example.com", refused.host)
        assertEquals(emptyList<String>(), answered)
    }

    @Test
    fun `https, a lan host and an opted-in public host all go through`() {
        val client = client(Granted("seerr.example.com"))

        client.get("https://other.example.com/api/v1/status")
        client.get("http://seerr.lan:5055/api/v1/status")
        client.get("http://seerr.example.com/api/v1/status")

        assertEquals(3, answered.size)
        assertThrows(CleartextRefusedException::class.java) { client.get("http://other.example.com/api/v1/status") }
    }
}
