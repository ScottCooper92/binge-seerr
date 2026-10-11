package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The issue page's open sheet and its draft come back after a rotation (#1054): whatever is saved is restored, field
 * for field, including an empty page with nothing open.
 */
class IssueModalStateTest {
    private val scope = SaverScope { true }

    @Test
    fun `an open edit and an outbox draft survive the round trip`() {
        val state =
            IssueModalState().apply {
                composing = true
                confirmingStatus = true
                actingOnCommentId = 3
                deletingCommentId = 4
                outboxActionId = 7L
                openEdit(
                    IssueComment(
                        id = 5,
                        author = "ana",
                        authorId = 3,
                        isAdmin = false,
                        message = "Half typed",
                        createdAtMillis = null,
                        isMine = true,
                    ),
                )
                openOutboxEdit(
                    OutboxComment(
                        localId = 9L,
                        message = "Queued while offline",
                        author = "ana",
                        submittedAtMillis = 0L,
                        state = SendState.Failed(retryable = true),
                    ),
                )
            }

        val restored = roundTrip(state)

        assertEquals(true, restored.composing)
        assertEquals(true, restored.confirmingStatus)
        assertEquals(3, restored.actingOnCommentId)
        assertEquals(5, restored.editingCommentId)
        assertEquals("Half typed", restored.editDraft)
        assertEquals(4, restored.deletingCommentId)
        assertEquals(7L, restored.outboxActionId)
        assertEquals(9L, restored.editingOutboxId)
        assertEquals("Queued while offline", restored.outboxEditDraft)
    }

    @Test
    fun `a page with nothing open restores with nothing open`() {
        val restored = roundTrip(IssueModalState())

        assertFalse(restored.composing)
        assertNull(restored.editingCommentId)
        assertNull(restored.editingOutboxId)
        assertEquals("", restored.editDraft)
    }

    private fun roundTrip(state: IssueModalState): IssueModalState {
        val saver = IssueModalState.Saver
        val saved = with(saver) { scope.save(state) }
        return requireNotNull(saved?.let { saver.restore(it) })
    }
}
