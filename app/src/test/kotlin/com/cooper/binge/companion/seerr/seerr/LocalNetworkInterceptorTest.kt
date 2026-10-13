package com.cooper.binge.companion.seerr.seerr

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The cached client turns a call to a local-network server away while Android's permission is refused (#1507). */
class LocalNetworkInterceptorTest {
    private val reached = AtomicInteger()

    private fun client(permission: LocalNetworkPermission): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(SeerrLocalNetworkInterceptor(permission))
            .addInterceptor(
                Interceptor { chain ->
                    reached.incrementAndGet()
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

    @Test
    fun `a local server is refused, without a request, while the permission is not granted`() {
        val refused =
            assertThrows(LocalNetworkDeniedException::class.java) { client { false }.get("http://192.168.1.10:5055/api/v1/status") }

        assertEquals(0, reached.get())
        assertEquals(SeerrError.LocalNetworkDenied, refused.toSeerrError())
    }

    @Test
    fun `a name on the home network is refused too`() {
        assertThrows(LocalNetworkDeniedException::class.java) { client { false }.get("http://seerr.lan/api/v1/status") }
        assertThrows(LocalNetworkDeniedException::class.java) { client { false }.get("http://seerr:5055/api/v1/status") }
    }

    @Test
    fun `a local server is reached once the permission is granted, with no new client`() {
        var granted = false
        val client = client { granted }

        assertThrows(LocalNetworkDeniedException::class.java) { client.get("http://192.168.1.10:5055/api/v1/status") }
        granted = true
        client.get("http://192.168.1.10:5055/api/v1/status")

        assertEquals(1, reached.get())
    }

    @Test
    fun `a public, loopback or tailnet server never needs the permission`() {
        val client = client { false }

        client.get("https://seerr.example.com/api/v1/status")
        client.get("http://localhost:5055/api/v1/status")
        client.get("http://100.101.102.103:5055/api/v1/status")
        client.get("https://seerr.tail1234.ts.net/api/v1/status")

        assertEquals(4, reached.get())
    }
}
