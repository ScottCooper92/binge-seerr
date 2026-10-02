package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

private const val TIMEOUT_MILLIS = 15_000L
private const val MEDIA_SERVER_JELLYFIN = 2

/** Unchecked, because the proxy fake would wrap a checked exception the interface does not declare. */
private class ServerDown(
    message: String,
) : RuntimeException(message)

/** A [SeerrApi] answering only the two profile calls; any other method is a test bug. */
private fun fakeApi(
    status: suspend () -> SeerrStatusDto,
    settings: suspend () -> SeerrPublicSettings,
): SeerrApi =
    Proxy.newProxyInstance(SeerrApi::class.java.classLoader, arrayOf(SeerrApi::class.java)) { _, method, args ->
        @Suppress("UNCHECKED_CAST")
        val continuation = args.last() as Continuation<Any?>
        when (method.name) {
            "status" -> status.startCoroutineUninterceptedOrReturn(continuation)
            "publicSettings" -> settings.startCoroutineUninterceptedOrReturn(continuation)
            else -> error("unexpected call ${method.name}")
        }
    } as SeerrApi

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrProfileReadTest {
    private val statusDto = SeerrStatusDto(version = "3.4.0")
    private val settingsDto = SeerrPublicSettings(mediaServerType = MEDIA_SERVER_JELLYFIN, applicationTitle = "Home")

    @Test
    fun `the two calls run concurrently, so an unreachable server costs one timeout`() =
        runTest {
            val api =
                fakeApi(
                    status = {
                        delay(TIMEOUT_MILLIS)
                        throw ServerDown("status unreachable")
                    },
                    settings = {
                        delay(TIMEOUT_MILLIS)
                        throw ServerDown("settings unreachable")
                    },
                )

            val profile = api.readProfile(SeerrVariant.Jellyseerr)

            assertEquals(TIMEOUT_MILLIS, currentTime)
            assertEquals(SeerrServerProfile.unknown(SeerrVariant.Jellyseerr), profile)
        }

    @Test
    fun `both calls are in flight before either answers`() =
        runTest {
            val statusStarted = CompletableDeferred<Unit>()
            val settingsStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val api =
                fakeApi(
                    status = {
                        statusStarted.complete(Unit)
                        release.await()
                        statusDto
                    },
                    settings = {
                        settingsStarted.complete(Unit)
                        release.await()
                        settingsDto
                    },
                )

            val read = async { api.inspectProfile(SeerrVariant.Unknown) }
            runCurrent()

            assertTrue(statusStarted.isCompleted)
            assertTrue("settings is not held behind status", settingsStarted.isCompleted)
            release.complete(Unit)
            assertTrue(read.await().getOrThrow().complete)
        }

    @Test
    fun `both answering is the same profile as before`() =
        runTest {
            val api = fakeApi(status = { statusDto }, settings = { settingsDto })

            val profile = api.inspectProfile(SeerrVariant.Unknown).getOrThrow()

            assertEquals(SeerrServerProfile.from(statusDto, settingsDto), profile)
            assertTrue(profile.complete)
            assertEquals(SeerrVariant.Seerr, profile.variant)
        }

    @Test
    fun `a failed settings call leaves an incomplete profile read again next time`() =
        runTest {
            val api = fakeApi(status = { statusDto }, settings = { throw ServerDown("boom") })

            val profile = api.inspectProfile(SeerrVariant.Unknown).getOrThrow()

            assertEquals(SeerrServerProfile.from(statusDto, null), profile)
            assertFalse(profile.complete)
        }

    @Test
    fun `a failed status call takes the lineage from the settings`() =
        runTest {
            val api = fakeApi(status = { throw ServerDown("boom") }, settings = { settingsDto })

            val profile = api.inspectProfile(SeerrVariant.Overseerr).getOrThrow()

            assertEquals(SeerrServerProfile.from(SeerrStatusDto(), settingsDto), profile)
            assertEquals(SeerrVariant.Seerr, profile.variant)
        }

    @Test
    fun `a failed status call with settings that name no media server keeps the recorded lineage`() =
        runTest {
            val plain = SeerrPublicSettings(applicationTitle = "Home")
            val api = fakeApi(status = { throw ServerDown("boom") }, settings = { plain })

            val profile = api.inspectProfile(SeerrVariant.Jellyseerr).getOrThrow()

            assertEquals(SeerrServerProfile.unknown(SeerrVariant.Jellyseerr).copy(settings = plain), profile)
            assertFalse(profile.complete)
        }

    @Test
    fun `neither answering fails with the status error, and readProfile guesses the recorded lineage`() =
        runTest {
            val statusFailure = ServerDown("status")
            val api = fakeApi(status = { throw statusFailure }, settings = { throw ServerDown("settings") })

            val result = api.inspectProfile(SeerrVariant.Jellyseerr)

            assertSame(statusFailure, result.exceptionOrNull())
            assertEquals(SeerrServerProfile.unknown(SeerrVariant.Jellyseerr), api.readProfile(SeerrVariant.Jellyseerr))
        }

    @Test
    fun `a slow settings call still decides the profile, however fast status failed`() =
        runTest {
            val api =
                fakeApi(
                    status = { throw ServerDown("fast") },
                    settings = {
                        delay(TIMEOUT_MILLIS)
                        settingsDto
                    },
                )

            val profile = api.inspectProfile(SeerrVariant.Unknown).getOrThrow()

            assertEquals(SeerrVariant.Seerr, profile.variant)
            assertEquals(TIMEOUT_MILLIS, currentTime)
        }

    @Test
    fun `cancelling the read cancels both calls and is not swallowed`() =
        runTest {
            var statusCancelled = false
            var settingsCancelled = false
            val api =
                fakeApi(
                    status = {
                        try {
                            awaitCancellation()
                        } finally {
                            statusCancelled = true
                        }
                    },
                    settings = {
                        try {
                            awaitCancellation()
                        } finally {
                            settingsCancelled = true
                        }
                    },
                )
            var outcome: Throwable? = null
            val read =
                launch {
                    try {
                        api.inspectProfile(SeerrVariant.Unknown)
                    } catch (e: CancellationException) {
                        outcome = e
                        throw e
                    }
                }
            advanceTimeBy(TIMEOUT_MILLIS)
            runCurrent()

            read.cancel()
            read.join()

            assertTrue(statusCancelled)
            assertTrue(settingsCancelled)
            assertTrue(outcome is CancellationException)
        }

    @Test
    fun `a call that throws cancellation ends the read instead of becoming a failed call`() =
        runTest {
            val api =
                fakeApi(
                    status = { throw CancellationException("stop") },
                    settings = {
                        delay(TIMEOUT_MILLIS)
                        settingsDto
                    },
                )

            try {
                api.inspectProfile(SeerrVariant.Unknown)
                fail("expected cancellation")
            } catch (expected: CancellationException) {
                assertEquals("stop", expected.message)
            }
        }
}
