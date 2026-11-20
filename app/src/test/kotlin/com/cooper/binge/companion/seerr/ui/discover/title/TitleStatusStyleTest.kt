package com.cooper.binge.companion.seerr.ui.discover.title

import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleStatusStyleTest {
    @Test
    fun `every status has its one label and colour`() {
        assertEquals(StatusStyle(R.string.hubs_card_requested, BingeSentiment.Caution), TitleAvailability.Pending.statusStyle())
        assertEquals(StatusStyle(R.string.hubs_card_processing, BingeSentiment.Info), TitleAvailability.Processing.statusStyle())
        assertEquals(StatusStyle(R.string.hubs_card_partial, BingeSentiment.Info), TitleAvailability.PartiallyAvailable.statusStyle())
        assertEquals(StatusStyle(R.string.hubs_card_available, BingeSentiment.Positive), TitleAvailability.Available.statusStyle())
        assertEquals(StatusStyle(R.string.hubs_card_blocklisted, BingeSentiment.Negative), TitleAvailability.Blocklisted.statusStyle())
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
        assertEquals(R.string.title_page_request, TitleState.Request.labelRes())
        assertEquals(R.string.request_state_declined, TitleState.Declined.labelRes())
    }

    @Test
    fun `every availability is reached from a title page state`() {
        assertEquals(TitleAvailability.entries.toSet(), TitleState.entries.map { it.toAvailability() }.toSet())
    }
}
