package com.cooper.binge.companion.seerr.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each tab keeps its own pages, and choosing a tab again clears only that tab's. */
class TvOpenPagesTest {
    @Test
    fun `a tab with nothing open has no pages`() {
        val pages = TvOpenPages()

        assertFalse(pages.anyOpen(TvDestination.Hub))
        assertFalse(pages.anyOpen(TvDestination.Issues))
    }

    @Test
    fun `a page belongs to its own tab`() {
        val pages = TvOpenPages(openRequestId = 1)

        assertTrue(pages.anyOpen(TvDestination.Hub))
        assertFalse(pages.anyOpen(TvDestination.Issues))
    }

    @Test
    fun `clearing Home leaves Issues' pages open`() {
        val pages = TvOpenPages(openRequestId = 1, seeAllRequests = "Pending", seeAllAccount = true, openIssueId = 2)

        pages.clear(TvDestination.Hub)

        assertFalse(pages.anyOpen(TvDestination.Hub))
        assertEquals(2, pages.openIssueId)
    }

    @Test
    fun `clearing Issues leaves Home's pages open`() {
        val pages = TvOpenPages(openRequestId = 1, seeAllIssues = "Open", openIssueId = 2)

        pages.clear(TvDestination.Issues)

        assertNull(pages.openIssueId)
        assertNull(pages.seeAllIssues)
        assertEquals(1, pages.openRequestId)
    }
}
