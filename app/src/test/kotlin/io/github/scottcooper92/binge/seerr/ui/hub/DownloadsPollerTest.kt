package io.github.scottcooper92.binge.seerr.ui.hub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

private const val ACTIVE_INTERVAL_MS = 10_000L
private const val IDLE_INTERVAL_MS = 60_000L

class DownloadsPollerTest {
    private val healthy = MutableStateFlow(true)
    private var fetches = 0
    private var answer: Result<List<HubDownload>> = Result.success(listOf(download(1)))

    private fun TestScope.poller() =
        DownloadsPoller(scope = backgroundScope, healthy = healthy) {
            fetches++
            answer
        }

    @Test
    fun `polls on the fast interval while something downloads, only while visible`() =
        runTest {
            val poller = poller()
            runCurrent()
            assertEquals(0, fetches)

            poller.setScreenVisible(true)
            runCurrent()
            assertEquals(1, fetches)

            advanceTimeBy(ACTIVE_INTERVAL_MS)
            runCurrent()
            assertEquals(2, fetches)

            poller.setScreenVisible(false)
            advanceTimeBy(ACTIVE_INTERVAL_MS * 3)
            runCurrent()
            assertEquals(2, fetches)
        }

    @Test
    fun `an empty strip slows the poll to the idle interval`() =
        runTest {
            answer = Result.success(emptyList())
            val poller = poller()
            poller.setScreenVisible(true)
            runCurrent()
            assertEquals(1, fetches)

            advanceTimeBy(ACTIVE_INTERVAL_MS * 3)
            runCurrent()
            assertEquals(1, fetches)

            advanceTimeBy(IDLE_INTERVAL_MS)
            runCurrent()
            assertEquals(2, fetches)
        }

    @Test
    fun `polling stops while the server is unhealthy and never starts until it is healthy`() =
        runTest {
            healthy.value = false
            val poller = poller()
            poller.setScreenVisible(true)
            advanceTimeBy(ACTIVE_INTERVAL_MS * 3)
            runCurrent()
            assertEquals(0, fetches)

            healthy.value = true
            runCurrent()
            assertEquals(1, fetches)

            healthy.value = false
            advanceTimeBy(ACTIVE_INTERVAL_MS * 3)
            runCurrent()
            assertEquals(1, fetches)
        }

    @Test
    fun `the strip is capped at five, and a failed fetch keeps the last one`() =
        runTest {
            answer = Result.success((1..8).map { download(it) })
            val poller = poller()
            poller.setScreenVisible(true)
            runCurrent()
            assertEquals((1..5).map { download(it) }, poller.downloading.value)

            answer = Result.failure(IOException())
            advanceTimeBy(ACTIVE_INTERVAL_MS)
            runCurrent()
            assertEquals(2, fetches)
            assertEquals((1..5).map { download(it) }, poller.downloading.value)
        }

    private fun download(id: Int) =
        HubDownload(requestId = id, title = "Title $id", posterUrl = null, fraction = 0.5f, totalBytes = 1L, etaMinutes = 1)
}
