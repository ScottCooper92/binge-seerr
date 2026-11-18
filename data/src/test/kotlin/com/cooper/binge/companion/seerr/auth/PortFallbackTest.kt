package com.cooper.binge.companion.seerr.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The portless-address retry, with no network at all. */
class PortFallbackTest {
    @Test
    fun `withPortFallback tries an explicit-port address exactly once, whatever it fails with`() =
        runTest {
            var calls = 0

            val result =
                withPortFallback("http://192.168.1.10:5055/") { candidate ->
                    calls++
                    assertEquals("http://192.168.1.10:5055/", candidate)
                    Result.failure<Unit>(NotSeerrServerException(IllegalStateException("boom")))
                }

            assertTrue(result.isFailure)
            assertEquals(1, calls)
        }

    @Test
    fun `withPortFallback retries a portless address on an unreachable primary`() =
        runTest {
            val attempts = mutableListOf<String>()

            val result =
                withPortFallback("http://192.168.1.10/") { candidate ->
                    attempts += candidate
                    if (candidate == "http://192.168.1.10:5055/") Result.success("ok") else Result.failure(IOException("refused"))
                }

            assertEquals("ok", result.getOrNull())
            assertEquals(listOf("http://192.168.1.10/", "http://192.168.1.10:5055/"), attempts)
        }

    @Test
    fun `withPortFallback retries a portless address that answered but was not a Seerr server too`() =
        runTest {
            val attempts = mutableListOf<String>()

            val result =
                withPortFallback("http://192.168.1.10/") { candidate ->
                    attempts += candidate
                    if (candidate == "http://192.168.1.10:5055/") {
                        Result.success("ok")
                    } else {
                        Result.failure(NotSeerrServerException(IllegalStateException("not seerr")))
                    }
                }

            assertEquals("ok", result.getOrNull())
            assertEquals(2, attempts.size)
        }

    @Test
    fun `withPortFallback reports the primary's failure when the retry fails too`() =
        runTest {
            val primaryFailure = IOException("refused")

            val result =
                withPortFallback<Unit>("http://192.168.1.10/") { candidate ->
                    if (candidate == "http://192.168.1.10:5055/") {
                        Result.failure(NotSeerrServerException(IllegalStateException("also not seerr")))
                    } else {
                        Result.failure(primaryFailure)
                    }
                }

            assertEquals(primaryFailure, result.exceptionOrNull())
        }

    @Test
    fun `withPortFallback keeps the scheme and base path on the retry`() =
        runTest {
            val attempts = mutableListOf<String>()

            withPortFallback("https://seerr.example.com/media/") { candidate ->
                attempts += candidate
                Result.failure<Unit>(IOException("refused"))
            }

            assertEquals(listOf("https://seerr.example.com/media/", "https://seerr.example.com:5055/media/"), attempts)
        }

    @Test
    fun `withPortFallback treats a typed default port as no port, so a failed probe retries at the Seerr port`() =
        runTest {
            val attempts = mutableListOf<String>()

            val result =
                withPortFallback("https://seerr.example.com:443/") { candidate ->
                    attempts += candidate
                    Result.failure<Unit>(IOException("refused"))
                }

            assertTrue(result.isFailure)
            assertEquals(2, attempts.size)
            assertTrue(attempts.last().contains(":5055"))
        }
}
