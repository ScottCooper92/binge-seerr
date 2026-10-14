package com.cooper.binge.companion.seerr.ui.settings.server

import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import org.junit.Assert.assertEquals
import org.junit.Test

/** #1546: the library scan's job id is the media server's own, as upstream's `server/job/schedule.ts` registers it. */
class MediaServerScanJobIdTest {
    private fun job(id: String) = ServerJob(id, id, JobInterval.Long, running = false, nextRunMillis = null)

    @Test
    fun `the server's own job list decides the id`() {
        val jellyfinJobs = listOf(job("download-sync"), job(JELLYFIN_FULL_SCAN_JOB_ID), job("jellyfin-recently-added-scan"))

        assertEquals(JELLYFIN_FULL_SCAN_JOB_ID, mediaServerScanJobId(jellyfinJobs, SeerrMediaServer.Unknown))
        assertEquals(PLEX_FULL_SCAN_JOB_ID, mediaServerScanJobId(listOf(job(PLEX_FULL_SCAN_JOB_ID)), SeerrMediaServer.Unknown))
    }

    @Test
    fun `before the job list loads, the media server type decides`() {
        assertEquals(JELLYFIN_FULL_SCAN_JOB_ID, mediaServerScanJobId(null, SeerrMediaServer.Jellyfin))
        assertEquals(JELLYFIN_FULL_SCAN_JOB_ID, mediaServerScanJobId(null, SeerrMediaServer.Emby))
        assertEquals(PLEX_FULL_SCAN_JOB_ID, mediaServerScanJobId(null, SeerrMediaServer.Plex))
    }

    @Test
    fun `a job list with no scan falls back to the type`() {
        assertEquals(JELLYFIN_FULL_SCAN_JOB_ID, mediaServerScanJobId(listOf(job("download-sync")), SeerrMediaServer.Jellyfin))
    }
}
