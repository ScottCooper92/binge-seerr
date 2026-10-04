package io.github.scottcooper92.binge.seerr.telemetry

import io.github.scottcooper92.binge.seerr.util.RecordingAnalytics
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Which failures say something about the server's version, and so are reported (#539, #689). */
class OperationFailuresTest {
    private val analytics = RecordingAnalytics()

    private fun http(
        code: Int,
        vararg headers: Pair<String, String>,
    ): HttpException {
        val raw =
            okhttp3.Response
                .Builder()
                .request(Request.Builder().url("http://fake-seerr.lan:8080/api/v1/request").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
        return HttpException(Response.error<Unit>("{}".toResponseBody("application/json".toMediaType()), raw))
    }

    private fun reported(failure: Throwable): List<String> {
        analytics.operationFailed("approve_request", failure, profile = null)
        return analytics.events.map { (_, params) -> params.getValue(AnalyticsEvents.PARAM_HTTP_STATUS).toString() }
    }

    @Test
    fun `a 401 is the session and is not reported`() {
        assertEquals(emptyList<String>(), reported(http(401)))
    }

    @Test
    fun `a 403 marked as a rejected session is not reported`() {
        assertEquals(emptyList<String>(), reported(http(403, "X-Binge-Session-Rejected" to "true")))
    }

    @Test
    fun `an unmarked 403 is a permission and is reported`() {
        assertEquals(listOf("403"), reported(http(403)))
    }

    @Test
    fun `a 400 is reported`() {
        assertEquals(listOf("400"), reported(http(400)))
    }
}
