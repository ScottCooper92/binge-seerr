package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.TitleRequest
import com.cooper.binge.companion.seerr.discover.title.TitleVideo
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleActionsModelTest {
    private val open = VersionState(TitleAvailability.None, mayRequest = true)

    private fun held(availability: TitleAvailability) = VersionState(availability, mayRequest = false)

    private fun request(
        id: Int,
        status: SeerrRequestStatusCode = SeerrRequestStatusCode.Pending,
    ) = TitleRequest(id, is4k = false, status = status, requestedBy = null, createdAt = null)

    private fun primary(
        standard: VersionState,
        fourK: VersionState? = null,
        requests: List<TitleRequest> = emptyList(),
    ) = titleActionsModel(standard, fourK, requests).primary

    @Test
    fun `an open standard version is a Request that opens the sheet`() {
        assertEquals(TitlePrimary(TitleState.Request, fourKOnly = false, tap = PrimaryTap.RequestSheet), primary(open))
    }

    @Test
    fun `an open standard version stays a Request while 4K is already being fetched`() {
        assertEquals(TitleState.Request, primary(open, held(TitleAvailability.Processing)).state)
    }

    @Test
    fun `only 4K open reads as Request in 4K`() {
        assertEquals(
            TitlePrimary(TitleState.Request, fourKOnly = true, tap = PrimaryTap.RequestSheet),
            primary(held(TitleAvailability.None), open),
        )
    }

    @Test
    fun `the best state of the two versions wins`() {
        assertEquals(TitleState.Available, primary(held(TitleAvailability.Pending), held(TitleAvailability.Available)).state)
        assertEquals(
            TitleState.PartiallyAvailable,
            primary(held(TitleAvailability.Processing), held(TitleAvailability.PartiallyAvailable)).state,
        )
        assertEquals(TitleState.Processing, primary(held(TitleAvailability.Pending), held(TitleAvailability.Processing)).state)
        assertEquals(TitleState.Requested, primary(held(TitleAvailability.Blocklisted), held(TitleAvailability.Pending)).state)
    }

    @Test
    fun `a held title opens the newest request that was not declined`() {
        val requests = listOf(request(3, SeerrRequestStatusCode.Declined), request(2), request(1))
        assertEquals(PrimaryTap.ManageRequest(2), primary(held(TitleAvailability.Pending), requests = requests).tap)
    }

    @Test
    fun `when every request was declined the newest is still the one to open`() {
        val requests = listOf(request(1, SeerrRequestStatusCode.Declined), request(4, SeerrRequestStatusCode.Declined))
        assertEquals(PrimaryTap.ManageRequest(4), primary(held(TitleAvailability.Pending), requests = requests).tap)
    }

    @Test
    fun `a held title with no request on file opens the sheet only if a version is still open`() {
        assertEquals(PrimaryTap.RequestSheet, primary(held(TitleAvailability.Available), open).tap)
        assertEquals(PrimaryTap.Nothing, primary(held(TitleAvailability.Available)).tap)
    }

    @Test
    fun `a partly available series that can still be requested opens the request sheet for the remaining seasons`() {
        val partly = VersionState(TitleAvailability.PartiallyAvailable, mayRequest = true)
        assertEquals(PrimaryTap.RequestSheet, primary(partly, requests = listOf(request(7))).tap)
    }

    @Test
    fun `a partly available series with nothing left to request manages its request`() {
        val partly = VersionState(TitleAvailability.PartiallyAvailable, mayRequest = false)
        assertEquals(PrimaryTap.ManageRequest(7), primary(partly, requests = listOf(request(7))).tap)
    }

    @Test
    fun `a user who may not request and a title the server holds nothing of has a disabled primary`() {
        assertEquals(TitlePrimary(TitleState.Request, fourKOnly = false, tap = PrimaryTap.Nothing), primary(held(TitleAvailability.None)))
    }

    @Test
    fun `a blocklisted title has nothing to open`() {
        assertEquals(
            TitlePrimary(TitleState.Blocklisted, fourKOnly = false, tap = PrimaryTap.Nothing),
            primary(held(TitleAvailability.Blocklisted)),
        )
    }

    @Test
    fun `report is offered once the title is in the library in whole or in part`() {
        fun canReport(availability: TitleAvailability) = titleActionsModel(held(availability), null, emptyList()).canReport

        assertTrue(canReport(TitleAvailability.Available))
        assertTrue(canReport(TitleAvailability.PartiallyAvailable))
        assertFalse(canReport(TitleAvailability.None))
        assertFalse(canReport(TitleAvailability.Pending))
        assertFalse(canReport(TitleAvailability.Processing))
        assertFalse(canReport(TitleAvailability.Blocklisted))
    }

    @Test
    fun `the trailer is the first clip the server types as one`() {
        val videos = listOf(TitleVideo("Teaser", "t", isTrailer = false), TitleVideo("Trailer", "u", isTrailer = true))
        assertEquals("u", videos.trailerUrl())
        assertNull(listOf(TitleVideo("Clip", "c")).trailerUrl())
    }
}
