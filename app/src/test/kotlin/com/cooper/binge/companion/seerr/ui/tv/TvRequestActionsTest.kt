package com.cooper.binge.companion.seerr.ui.tv

import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.Approve
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.Block
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.Decline
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.Edit
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.MarkAs
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.OpenInBinge
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.OpenInMediaServer
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.Remove
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.Report
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestAction.Retry
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestActionSplit
import com.cooper.binge.companion.seerr.ui.tv.requests.availableRequestActions
import com.cooper.binge.companion.seerr.ui.tv.requests.splitLeads
import com.cooper.binge.companion.seerr.util.requestDetail
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which actions the TV request page offers, and which lead the row ahead of Manage. */
class TvRequestActionsTest {
    @Test
    fun `a decision leads, and everything else is managed in order, the destructive ones last`() {
        assertEquals(
            TvRequestActionSplit(leads = listOf(Approve, Decline), managed = listOf(OpenInMediaServer, Edit, Report, Block, Remove)),
            listOf(Approve, Decline, OpenInMediaServer, Edit, Report, Block, Remove).splitLeads(),
        )
    }

    @Test
    fun `a failed request leads with Retry`() {
        assertEquals(listOf(Retry), listOf(Retry, Remove).splitLeads().leads)
    }

    @Test
    fun `with no decision the title's own actions lead, three at most`() {
        assertEquals(
            TvRequestActionSplit(leads = listOf(OpenInMediaServer, OpenInBinge, MarkAs), managed = listOf(Edit, Report, Remove)),
            listOf(OpenInMediaServer, Edit, MarkAs, Report, OpenInBinge, Remove).splitLeads(),
        )
        assertEquals(listOf(OpenInBinge, MarkAs, Report), listOf(MarkAs, Report, OpenInBinge, Remove).splitLeads().leads)
    }

    @Test
    fun `each title action leads only where it is offered`() {
        assertEquals(listOf(OpenInMediaServer, Report), listOf(OpenInMediaServer, Report, Remove).splitLeads().leads)
        assertEquals(listOf(OpenInBinge, Report), listOf(Edit, Report, OpenInBinge, Remove).splitLeads().leads)
    }

    @Test
    fun `a full row of the title's actions keeps its Manage`() {
        assertEquals(
            TvRequestActionSplit(leads = listOf(OpenInMediaServer, OpenInBinge, MarkAs), managed = listOf(Edit, Report)),
            listOf(OpenInMediaServer, Edit, MarkAs, Report, OpenInBinge).splitLeads(),
        )
    }

    @Test
    fun `with nothing to lead, everything is in Manage`() {
        assertEquals(TvRequestActionSplit(leads = emptyList(), managed = listOf(Edit, Remove)), listOf(Edit, Remove).splitLeads())
    }

    @Test
    fun `a single action left over joins the row rather than a Manage of one`() {
        assertEquals(TvRequestActionSplit(leads = listOf(OpenInBinge), managed = emptyList()), listOf(OpenInBinge).splitLeads())
        assertEquals(TvRequestActionSplit(leads = listOf(MarkAs, Edit), managed = emptyList()), listOf(Edit, MarkAs).splitLeads())
        assertEquals(TvRequestActionSplit(leads = listOf(Report, Edit), managed = emptyList()), listOf(Edit, Report).splitLeads())
    }

    @Test
    fun `a single destructive action stays in Manage, and a full row keeps its Manage`() {
        assertEquals(TvRequestActionSplit(leads = emptyList(), managed = listOf(Remove)), listOf(Remove).splitLeads())
        assertEquals(
            TvRequestActionSplit(leads = listOf(Approve, Decline), managed = listOf(Edit)),
            listOf(Approve, Decline, Edit).splitLeads(),
        )
    }

    @Test
    fun `each action is offered only through its own gate`() {
        val none = availableRequestActions(requestDetail(), canEdit = true, canOpenInMediaServer = false, canOpenInBinge = false)
        assertEquals(emptyList<TvRequestAction>(), none)

        val all =
            availableRequestActions(
                requestDetail(
                    actions = RequestActions(canApprove = true, canDecline = true, canRemove = true, canBlock = true),
                    canEdit = true,
                    canReportIssue = true,
                ),
                canEdit = true,
                canOpenInMediaServer = true,
                canOpenInBinge = true,
            )
        assertEquals(listOf(Approve, Decline, OpenInMediaServer, Edit, Report, OpenInBinge, Block, Remove), all)
    }

    @Test
    fun `an issue needs the title on the server`() {
        val actions =
            availableRequestActions(
                requestDetail(canReportIssue = true, mediaId = null),
                canEdit = false,
                canOpenInMediaServer = false,
                canOpenInBinge = false,
            )
        assertEquals(emptyList<TvRequestAction>(), actions)
    }
}
