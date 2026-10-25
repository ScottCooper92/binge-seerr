package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.SeerrError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which outcomes leave the request page with nothing to show, so it closes (#1054). */
class ModerationEventTest {
    @Test
    fun `a removal, with or without its block, clearing the media, and deleting files with this request remove it`() {
        val removing =
            listOf(
                ModerationEvent.Approved,
                ModerationEvent.Retried,
                ModerationEvent.Edited,
                ModerationEvent.Declined,
                ModerationEvent.DeclinedAndBlocked,
                ModerationEvent.DeclinedButBlockFailed,
                ModerationEvent.Removed,
                ModerationEvent.RemovedAndBlocked,
                ModerationEvent.RemovedButBlockFailed,
                ModerationEvent.Blocked,
                ModerationEvent.BlockFailed,
                ModerationEvent.MediaStatusSet,
                ModerationEvent.MediaCleared,
                ModerationEvent.MediaFilesDeleted,
                ModerationEvent.MediaFilesAndRequestsDeleted(removedThisRequest = false),
                ModerationEvent.MediaFilesAndRequestsDeleted(removedThisRequest = true),
                ModerationEvent.MediaFilesDeletedRequestsKept(removedThisRequest = false),
                ModerationEvent.MediaFilesDeletedRequestsKept(removedThisRequest = true),
                ModerationEvent.MediaFilesNoDefaultServer,
                ModerationEvent.Failed(SeerrError.Server),
            ).filter { it.removesTheRequest }

        assertEquals(
            listOf(
                ModerationEvent.Removed,
                ModerationEvent.RemovedAndBlocked,
                ModerationEvent.RemovedButBlockFailed,
                ModerationEvent.MediaCleared,
                ModerationEvent.MediaFilesAndRequestsDeleted(removedThisRequest = true),
                ModerationEvent.MediaFilesDeletedRequestsKept(removedThisRequest = true),
            ),
            removing,
        )
    }

    /** Seerr's refusal for want of a default server shows in place of the delete row, so it raises no snackbar (#1726). */
    @Test
    fun `only the refusal shown in place has no snackbar line`() {
        assertNull(ModerationEvent.MediaFilesNoDefaultServer.messageRes())
        assertTrue(ModerationEvent.MediaFilesNoDefaultServer.isError())
        assertNotNull(ModerationEvent.Failed(SeerrError.Rejected).messageRes())
    }
}
