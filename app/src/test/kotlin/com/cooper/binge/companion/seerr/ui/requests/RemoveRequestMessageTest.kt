package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.util.requestItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the Remove confirm says a download or files stay, and when it may name Delete files (#1721). */
class RemoveRequestMessageTest {
    private val downloading = RequestDownload(fraction = 0.4f, etaMinutes = 10, downloading = true)

    @Test
    fun `a download in the queue or files on disk stay when the request goes`() {
        assertTrue(requestItem(download = downloading).keepsFilesOnRemove)
        assertTrue(requestItem(mediaStatus = SeerrMediaStatusCode.Available).keepsFilesOnRemove)
        assertTrue(requestItem(mediaStatus = SeerrMediaStatusCode.PartiallyAvailable).keepsFilesOnRemove)
    }

    @Test
    fun `nothing grabbed and nothing on disk leaves nothing to mention`() {
        listOf(null, SeerrMediaStatusCode.Pending, SeerrMediaStatusCode.Processing, SeerrMediaStatusCode.Deleted).forEach {
            assertFalse("$it", requestItem(mediaStatus = it).keepsFilesOnRemove)
        }
    }

    @Test
    fun `Delete files is named only where the sheet has its row for the request's version`() {
        val item = requestItem(is4k = true)
        val standard = instance(is4k = false)
        val fourK = instance(is4k = true)

        assertTrue(record(fourK).offersDeleteFiles(item))
        assertFalse(record(standard).offersDeleteFiles(item))
        assertFalse(record(fourK, canDeleteFiles = false).offersDeleteFiles(item))
        assertFalse(record(fourK.copy(filesDeletion = FilesDeletion.Deleted)).offersDeleteFiles(item))
        assertFalse(record(fourK.copy(status = SeerrMediaStatusCode.Unknown)).offersDeleteFiles(item))
        assertFalse(null.offersDeleteFiles(item))
    }

    private fun instance(is4k: Boolean) =
        MediaInstance(is4k = is4k, status = SeerrMediaStatusCode.Available, serviceUrl = null, mediaServerUrl = null, watch = null)

    private fun record(
        instance: MediaInstance,
        canDeleteFiles: Boolean = true,
    ) = MediaRecord(
        mediaId = 1,
        isTv = false,
        instances = listOf(instance),
        canSetStatus = false,
        canClearData = false,
        canDeleteFiles = canDeleteFiles,
    )
}
