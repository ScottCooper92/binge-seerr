package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a card says of a title that has a standard and a 4K version (#2590). */
class CardAvailabilityTest {
    private val order =
        listOf(
            TitleAvailability.None,
            TitleAvailability.Pending,
            TitleAvailability.Processing,
            TitleAvailability.PartiallyAvailable,
            TitleAvailability.Available,
            TitleAvailability.Blocklisted,
        )

    private val versions =
        listOf(
            null to TitleAvailability.None,
            SeerrMediaStatusCode.Unknown to TitleAvailability.None,
            SeerrMediaStatusCode.Pending to TitleAvailability.Pending,
            SeerrMediaStatusCode.Processing to TitleAvailability.Processing,
            SeerrMediaStatusCode.PartiallyAvailable to TitleAvailability.PartiallyAvailable,
            SeerrMediaStatusCode.Available to TitleAvailability.Available,
            SeerrMediaStatusCode.Blocklisted to TitleAvailability.Blocklisted,
        )

    @Test
    fun `the availabilities are declared in the order a card ranks them`() {
        assertEquals(order, TitleAvailability.entries)
    }

    @Test
    fun `every pair of versions shows the more advanced one`() {
        versions.forEach { (standard, standardShown) ->
            versions.forEach { (fourK, fourKShown) ->
                val expected = if (order.indexOf(standardShown) >= order.indexOf(fourKShown)) standardShown else fourKShown
                assertEquals("standard $standard, 4K $fourK", expected, cardAvailability(standard, fourK))
            }
        }
    }

    @Test
    fun `a title that is only in 4K shows its 4K status`() {
        assertEquals(TitleAvailability.Available, cardAvailability(null, SeerrMediaStatusCode.Available))
        assertEquals(TitleAvailability.Processing, cardAvailability(SeerrMediaStatusCode.Unknown, SeerrMediaStatusCode.Processing))
    }

    @Test
    fun `available in one version beats partial in the other`() {
        assertEquals(TitleAvailability.Available, cardAvailability(SeerrMediaStatusCode.PartiallyAvailable, SeerrMediaStatusCode.Available))
        assertEquals(TitleAvailability.Available, cardAvailability(SeerrMediaStatusCode.Available, SeerrMediaStatusCode.PartiallyAvailable))
    }

    @Test
    fun `a blocklisted version shows as blocklisted as it did`() {
        assertEquals(TitleAvailability.Blocklisted, cardAvailability(SeerrMediaStatusCode.Blocklisted, SeerrMediaStatusCode.Available))
    }
}
