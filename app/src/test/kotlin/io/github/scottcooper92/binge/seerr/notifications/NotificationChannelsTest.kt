package io.github.scottcooper92.binge.seerr.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationChannelsTest {
    @Test
    fun `the feeds have a channel each, the user's own requests share one, and the connection is apart`() {
        assertEquals(NotificationChannelKind.PendingRequests, NotificationSignal.PendingRequests.channel)
        assertEquals(NotificationChannelKind.OpenIssues, NotificationSignal.OpenIssues.channel)
        assertEquals(
            setOf(NotificationChannelKind.OwnRequests),
            listOf(NotificationSignal.RequestAvailable, NotificationSignal.RequestApproved, NotificationSignal.RequestDeclined)
                .mapTo(mutableSetOf()) { it.channel },
        )
        assertEquals(
            NotificationChannelKind.entries.size,
            NotificationChannelKind.entries
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun `the summary names the first title and counts the rest, and is absent without a title`() {
        val andMore = { first: String, others: Int -> "$first +$others" }
        assertNull(summaryLine(emptyList(), 3, andMore))
        assertEquals("Heat", summaryLine(listOf("Heat"), 1, andMore))
        assertEquals("Heat +2", summaryLine(listOf("Heat", "Ronin"), 3, andMore))
    }
}
