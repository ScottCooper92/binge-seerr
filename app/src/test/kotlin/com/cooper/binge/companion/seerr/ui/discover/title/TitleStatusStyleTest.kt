package com.cooper.binge.companion.seerr.ui.discover.title

import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.cooper.binge.companion.seerr.core.ui.R as UiR
import com.cooper.binge.companion.seerr.feature.discover.R as DiscoverR

class TitleStatusStyleTest {
    @Test
    fun `every status has its one label and colour`() {
        assertEquals(StatusStyle(DiscoverR.string.hubs_card_requested, BingeSentiment.Caution), TitleAvailability.Pending.statusStyle())
        assertEquals(StatusStyle(DiscoverR.string.hubs_card_processing, BingeSentiment.Info), TitleAvailability.Processing.statusStyle())
        assertEquals(StatusStyle(DiscoverR.string.hubs_card_partial, BingeSentiment.Info), TitleAvailability.PartiallyAvailable.statusStyle())
        assertEquals(StatusStyle(DiscoverR.string.hubs_card_available, BingeSentiment.Positive), TitleAvailability.Available.statusStyle())
        assertEquals(StatusStyle(DiscoverR.string.hubs_card_blocklisted, BingeSentiment.Negative), TitleAvailability.Blocklisted.statusStyle())
    }

    @Test
    fun `a title the server holds nothing of shows no chip`() {
        assertNull(TitleAvailability.None.statusStyle())
    }

    /** The primary on the title page and a card for the same title must read the same, so they share the table. */
    @Test
    fun `the title page's state and the card's availability agree for every status`() {
        TitleState.entries.forEach { state ->
            val onlyOnThePage = state == TitleState.Request || state == TitleState.Declined
            assertEquals("$state", state.toAvailability().statusStyle()?.label, state.labelRes().takeUnless { onlyOnThePage })
        }
        assertEquals(DiscoverR.string.title_page_request, TitleState.Request.labelRes())
        assertEquals(UiR.string.request_state_declined, TitleState.Declined.labelRes())
    }

    /** Request is the one state with an icon and no dot colour of its own; the rest are drawn tonal with their dot. */
    @Test
    fun `the primary's icon and dot colour are pinned for every state`() {
        val expected =
            mapOf(
                TitleState.Request to (true to BingeSentiment.Neutral),
                TitleState.Declined to (false to BingeSentiment.Negative),
                TitleState.Requested to (false to BingeSentiment.Caution),
                TitleState.Processing to (false to BingeSentiment.Info),
                TitleState.PartiallyAvailable to (false to BingeSentiment.Info),
                TitleState.Available to (false to BingeSentiment.Positive),
                TitleState.Blocklisted to (false to BingeSentiment.Negative),
            )
        assertEquals(TitleState.entries.toSet(), expected.keys)
        expected.forEach { (state, look) ->
            assertEquals("$state icon", look.first, state.icon() != null)
            assertEquals("$state dot", look.second, state.sentiment())
        }
    }

    @Test
    fun `every availability is reached from a title page state`() {
        assertEquals(TitleAvailability.entries.toSet(), TitleState.entries.map { it.toAvailability() }.toSet())
    }
}
