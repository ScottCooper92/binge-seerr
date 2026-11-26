package com.cooper.binge.companion.seerr.ui.grid

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** The Rating and Votes rows must report the maximum as well as the minimum the sections can set. */
@RunWith(RobolectricTestRunner::class)
class DiscoverFilterRangeSummaryTest {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val locale = Locale.US

    @Test
    fun `a rating minimum keeps its half point`() {
        assertEquals("6.5 or higher", ratingSummary(6.5, null, resources, locale))
    }

    @Test
    fun `a rating maximum alone is not Any`() {
        assertEquals("Up to 8", ratingSummary(null, 8.0, resources, locale))
    }

    @Test
    fun `a rating range reads both ends`() {
        assertEquals("6.5 – 8", ratingSummary(6.5, 8.0, resources, locale))
    }

    @Test
    fun `no rating bound is null`() {
        assertNull(ratingSummary(null, null, resources, locale))
    }

    @Test
    fun `a vote count maximum alone is not Any`() {
        assertEquals("Up to 1,000 votes", voteSummary(null, 1000, resources, locale))
    }

    @Test
    fun `a vote count minimum alone has no maximum`() {
        assertEquals("100 votes or more", voteSummary(100, null, resources, locale))
    }

    @Test
    fun `a vote count range reads both ends`() {
        assertEquals("100 to 10,000 votes", voteSummary(100, 10_000, resources, locale))
    }

    @Test
    fun `no vote bound is null`() {
        assertNull(voteSummary(null, null, resources, locale))
    }
}
