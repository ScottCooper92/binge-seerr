package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.TitleVideo
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleActionsModelTest {
    private val open = VersionState(TitleAvailability.None, mayRequest = true)

    private fun held(availability: TitleAvailability) = VersionState(availability, mayRequest = false)

    @Test
    fun `an open standard version is a Request with nothing else to say`() {
        val model = titleActionsModel(open, null)
        assertEquals(TitlePrimary.Request(fourKOnly = false), model.primary)
        assertEquals(emptyList<VersionStatus>(), model.otherStatuses)
    }

    @Test
    fun `only 4K open reads as Request in 4K and keeps the standard status as a line`() {
        val model = titleActionsModel(held(TitleAvailability.Available), open)
        assertEquals(TitlePrimary.Request(fourKOnly = true), model.primary)
        assertEquals(listOf(VersionStatus(TitleAvailability.Available, fourK = false)), model.otherStatuses)
    }

    @Test
    fun `an open standard version keeps the 4K status as a line`() {
        val model = titleActionsModel(open, held(TitleAvailability.Pending))
        assertEquals(TitlePrimary.Request(fourKOnly = false), model.primary)
        assertEquals(listOf(VersionStatus(TitleAvailability.Pending, fourK = true)), model.otherStatuses)
    }

    @Test
    fun `with nothing to request the standard status is the primary and 4K is the line`() {
        val model = titleActionsModel(held(TitleAvailability.Available), held(TitleAvailability.Pending))
        assertEquals(TitlePrimary.Status(VersionStatus(TitleAvailability.Available, fourK = false)), model.primary)
        assertEquals(listOf(VersionStatus(TitleAvailability.Pending, fourK = true)), model.otherStatuses)
    }

    @Test
    fun `a partly available series that can still be requested keeps its status as a line`() {
        val model = titleActionsModel(VersionState(TitleAvailability.PartiallyAvailable, mayRequest = true), null)
        assertEquals(TitlePrimary.Request(fourKOnly = false), model.primary)
        assertEquals(listOf(VersionStatus(TitleAvailability.PartiallyAvailable, fourK = false)), model.otherStatuses)
    }

    @Test
    fun `a user who may not request and a title the server holds nothing of has no primary`() {
        assertEquals(TitleActionsModel(TitlePrimary.None, emptyList()), titleActionsModel(held(TitleAvailability.None), null))
    }

    @Test
    fun `the trailer is the first clip the server types as one`() {
        val videos = listOf(TitleVideo("Teaser", "t", isTrailer = false), TitleVideo("Trailer", "u", isTrailer = true))
        assertEquals("u", videos.trailerUrl())
        assertNull(listOf(TitleVideo("Clip", "c")).trailerUrl())
    }
}
