package com.cooper.binge.companion.seerr.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each tab keeps its own pages, choosing a tab again clears only that tab's, and a withdrawn tab hands over to the start tab. */
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

    @Test
    fun `reconciling keeps an offered tab and every offered tab's pages`() {
        val pages = TvOpenPages(openRequestId = 1, openIssueId = 2)

        val tab = pages.reconcile(Both.last(), offered = Both)

        assertEquals(Both.last(), tab)
        Both.forEach { assertTrue(pages.anyOpen(it)) }
    }

    @Test
    fun `reconciling away from a withdrawn tab lands on the start tab and drops the withdrawn tab's pages`() {
        val pages = TvOpenPages(openRequestId = 1, openIssueId = 2)
        val offered = listOf(Both.first())

        val tab = pages.reconcile(Both.last(), offered)

        assertEquals(offered.first(), tab)
        assertFalse(pages.anyOpen(Both.last()))
        assertTrue(pages.anyOpen(offered.first()))
    }

    /** A tab withdrawn while another shows still loses its pages, so they do not come back when the tab does. */
    @Test
    fun `reconciling drops a withdrawn tab's pages even when it is not the one showing`() {
        val pages = TvOpenPages(openIssueId = 2)

        val tab = pages.reconcile(Both.first(), offered = listOf(Both.first()))

        assertEquals(Both.first(), tab)
        assertFalse(pages.anyOpen(Both.last()))
    }
}

/** The shell's two tabs today, the start tab first. */
private val Both = listOf(TvDestination.Hub, TvDestination.Issues)
