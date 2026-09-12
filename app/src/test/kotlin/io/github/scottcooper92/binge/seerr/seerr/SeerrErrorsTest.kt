package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.NotConnectedException
import io.grpc.Status
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/** The contract's error model, from this side: every Seerr failure becomes one of its documented codes. */
class SeerrErrorsTest {
    private fun http(
        code: Int,
        body: String = "{}",
    ): HttpException = HttpException(Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())))

    private fun codeOf(t: Throwable): Status.Code = t.toStatusException().status.code

    @Test
    fun `a rejected session is UNAUTHENTICATED, and so is no session at all`() {
        assertEquals(Status.Code.UNAUTHENTICATED, codeOf(http(401)))
        assertEquals(Status.Code.UNAUTHENTICATED, codeOf(NotConnectedException()))
    }

    @Test
    fun `a 403 is a permission refusal unless the body names a quota`() {
        assertEquals(Status.Code.PERMISSION_DENIED, codeOf(http(403, """{"message":"You do not have permission"}""")))
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, codeOf(http(403, """{"message":"Movie Quota exceeded"}""")))
    }

    @Test
    fun `transport failures and 5xx are UNAVAILABLE`() {
        assertEquals(Status.Code.UNAVAILABLE, codeOf(IOException("reset")))
        assertEquals(Status.Code.UNAVAILABLE, codeOf(SocketTimeoutException("timeout")))
        assertEquals(Status.Code.UNAVAILABLE, codeOf(http(502)))
    }

    @Test
    fun `other rejections are on the merits`() {
        assertEquals(Status.Code.NOT_FOUND, codeOf(http(404)))
        assertEquals(Status.Code.INVALID_ARGUMENT, codeOf(http(422)))
        assertEquals(Status.Code.INTERNAL, codeOf(IllegalStateException("bug")))
    }
}
