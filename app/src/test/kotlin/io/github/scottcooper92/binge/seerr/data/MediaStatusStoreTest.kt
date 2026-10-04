package io.github.scottcooper92.binge.seerr.data

import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.DownloadProgress
import com.binge.companion.contracts.request.v1.RequestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStatusStoreTest {
    @Test
    fun `a status survives the round trip`() {
        val status =
            RequestStatus
                .newBuilder()
                .setAvailability(Availability.AVAILABILITY_PARTIALLY_AVAILABLE)
                .setWatchUrl("https://plex.example/watch")
                .build()

        assertEquals(status, decodeStatus(encodeStatus(status)))
    }

    @Test
    fun `requester ids survive the round trip`() {
        val ids = mapOf(4 to 1, 6 to 2)

        assertEquals(ids, decodeRequesterIds(encodeRequesterIds(ids)))
        assertEquals(emptyMap<Int, Int>(), decodeRequesterIds(encodeRequesterIds(emptyMap())))
    }

    /** A pair that does not parse withholds an action rather than granting one. */
    @Test
    fun `a malformed requester pair is dropped`() {
        assertEquals(mapOf(4 to 1), decodeRequesterIds("4:1,x:2,7,8:9:10"))
    }

    /** A row this build cannot read sends the caller to the server; it never throws into the host's call. */
    @Test
    fun `a row that is not a status reads as a miss`() {
        assertNull(decodeStatus("not base64 at all"))
        assertNull(decodeStatus("//////////////////////8="))
    }

    @Test
    fun `the standard download survives the round trip, and none is the empty string`() {
        val download =
            DownloadProgress
                .newBuilder()
                .setFraction(0.5f)
                .setTotalBytes(1_000)
                .build()

        assertEquals(download, decodeDownload(encodeDownload(download)))
        assertEquals("", encodeDownload(null))
        assertNull(decodeDownload(""))
        assertNull(decodeDownload("not base64 at all"))
    }

    /** A status built without a separate standard download has no 4K one to leave out, so the two are the same. */
    @Test
    fun `a cached status defaults its standard download to its download`() {
        val download = DownloadProgress.newBuilder().setTotalBytes(1_000).build()

        assertEquals(download, CachedStatus(RequestStatus.newBuilder().setDownload(download).build(), 0).standardDownload)
        assertNull(CachedStatus(RequestStatus.getDefaultInstance(), 0).standardDownload)
    }
}
