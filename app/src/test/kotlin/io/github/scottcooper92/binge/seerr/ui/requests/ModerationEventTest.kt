package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which outcomes leave the request page with nothing to show, so it closes (#1054). */
class ModerationEventTest {
    @Test
    fun `a removal, with or without its block, and clearing the media remove the request`() {
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
                ModerationEvent.Failed(SeerrError.Server),
            ).filter { it.removesTheRequest }

        assertEquals(
            listOf(
                ModerationEvent.Removed,
                ModerationEvent.RemovedAndBlocked,
                ModerationEvent.RemovedButBlockFailed,
                ModerationEvent.MediaCleared,
            ),
            removing,
        )
    }
}
