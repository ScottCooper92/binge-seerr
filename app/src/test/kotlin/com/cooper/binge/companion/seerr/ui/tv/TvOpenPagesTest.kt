package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each tab keeps its own pages, choosing a tab again clears only that tab's, and a withdrawn tab hands over to the start tab. */
class TvOpenPagesTest {
    @Test
    fun `every tab has pages, and none is open to begin with`() {
        val pages = TvOpenPages()

        Tabs.forEach { assertFalse(pages.anyOpen(it)) }
    }

    @Test
    fun `a sheet has no pages`() {
        val pages = TvOpenPages()

        TvDestination.entries.filter { it.isSheet }.forEach { assertFalse(pages.anyOpen(it)) }
    }

    @Test
    fun `a page belongs to its own tab`() {
        val pages = TvOpenPages()

        pages[Tabs.first()].detailId = 1

        assertTrue(pages.anyOpen(Tabs.first()))
        assertFalse(pages.anyOpen(Tabs.last()))
    }

    @Test
    fun `clearing one tab leaves the other's pages open`() {
        val pages = withBothOpen()

        pages.clear(Tabs.first())

        assertNull(pages[Tabs.first()].detailId)
        assertNull(pages[Tabs.first()].seeAll)
        assertEquals(2, pages[Tabs.last()].detailId)
        assertEquals("Open", pages[Tabs.last()].seeAll)
    }

    @Test
    fun `reconciling keeps an offered tab and every offered tab's pages`() {
        val pages = withBothOpen()

        val tab = pages.reconcile(Tabs.last(), offered = Tabs)

        assertEquals(Tabs.last(), tab)
        Tabs.forEach { assertTrue(pages.anyOpen(it)) }
    }

    @Test
    fun `reconciling away from a withdrawn tab lands on the start tab and drops the withdrawn tab's pages`() {
        val pages = withBothOpen()
        val offered = listOf(Tabs.first())

        val tab = pages.reconcile(Tabs.last(), offered)

        assertEquals(offered.first(), tab)
        assertFalse(pages.anyOpen(Tabs.last()))
        assertTrue(pages.anyOpen(offered.first()))
    }

    /** A tab withdrawn while another shows still loses its pages, so they do not come back when the tab does. */
    @Test
    fun `reconciling drops a withdrawn tab's pages even when it is not the one showing`() {
        val pages = withBothOpen()

        val tab = pages.reconcile(Tabs.first(), offered = listOf(Tabs.first()))

        assertEquals(Tabs.first(), tab)
        assertFalse(pages.anyOpen(Tabs.last()))
    }

    /** The pages survive a rotation: each tab gets back its own detail and see-all, not its neighbour's. */
    @Test
    fun `saved pages come back to the tabs they were saved from`() {
        val saved = with(TvOpenPages.Saver) { SaverScope { true }.save(withBothOpen()) }

        val restored = TvOpenPages.Saver.restore(checkNotNull(saved))

        assertEquals(1, restored?.get(Tabs.first())?.detailId)
        assertEquals("Pending", restored?.get(Tabs.first())?.seeAll)
        assertEquals(2, restored?.get(Tabs.last())?.detailId)
        assertEquals("Open", restored?.get(Tabs.last())?.seeAll)
    }

    private fun withBothOpen() =
        TvOpenPages(
            mapOf(
                Tabs.first() to TvTabPages(detailId = 1, seeAll = "Pending"),
                Tabs.last() to TvTabPages(detailId = 2, seeAll = "Open"),
            ),
        )
}

/** The shell's tabs today, the start tab first. */
private val Tabs = TvDestination.entries.filterNot { it.isSheet }
