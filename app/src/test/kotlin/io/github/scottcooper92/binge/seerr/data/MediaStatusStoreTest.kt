package io.github.scottcooper92.binge.seerr.data

import com.binge.integration.contracts.request.v1.Availability
import com.binge.integration.contracts.request.v1.RequestStatus
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

    /** A row this build cannot read sends the caller to the server; it never throws into the host's call. */
    @Test
    fun `a row that is not a status reads as a miss`() {
        assertNull(decodeStatus("not base64 at all"))
        assertNull(decodeStatus("//////////////////////8="))
    }
}
