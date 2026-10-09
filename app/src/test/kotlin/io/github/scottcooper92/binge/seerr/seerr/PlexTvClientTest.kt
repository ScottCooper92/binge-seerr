package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Polling a plex.tv PIN answers with the Plex auth token, so the client never follows a redirect from `https` down to
 * `http` (#1033). A redirect that stays on one scheme is still followed.
 */
class PlexTvClientTest {
    private val client = plexTvClient(PlexClientIdentity(identifier = "cid", product = "Binge Seerr", version = "0.1.0", device = "Pixel"))

    @Test
    fun `the plex tv client never follows a redirect from https to http`() {
        assertFalse(client.followSslRedirects)
        assertTrue(client.followRedirects)
    }
}
