package com.cooper.binge.companion.seerr.ui.discover.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which of the title's other actions take a free round button, and where the rest go: Report to the bar, the others to More, which also always holds Open in browser. */
class TitleChromePlacementTest {
    private val all = TitleSpill(onTrailer = {}, onReport = {}, onShare = {}, onBlock = {})

    @Test
    fun `free slots take Trailer, Report, Share then Block, in that order`() {
        val placement = placeSpill(fixedRound = 1, all)

        assertTrue(placement.roundTrailer)
        assertTrue(placement.roundReport)
        assertTrue(placement.roundShare)
        assertFalse(placement.roundBlock)
    }

    @Test
    fun `what does not fit goes to More, and Report to the bar`() {
        val placement = placeSpill(fixedRound = ROUND_SLOTS, all)

        assertEquals(all.onReport, placement.barReport(all))
        assertEquals(all.onTrailer, placement.moreTrailer(all))
        assertEquals(all.onShare, placement.moreShare(all))
        assertEquals(all.onBlock, placement.moreBlock(all))
    }

    @Test
    fun `an action the title does not have takes no slot, so the next one moves up`() {
        val spill = TitleSpill(onTrailer = null, onReport = null, onShare = {}, onBlock = {})

        val placement = placeSpill(fixedRound = 2, spill)

        assertTrue(placement.roundShare)
        assertTrue(placement.roundBlock)
        assertNull(placement.moreShare(spill))
        assertNull(placement.moreBlock(spill))
        assertNull(placement.barReport(spill))
    }

    @Test
    fun `with every slot free nothing is left for the bar or More`() {
        val placement = placeSpill(fixedRound = 0, all)

        assertNull(placement.moreTrailer(all))
        assertNull(placement.moreShare(all))
        assertNull(placement.moreBlock(all))
        assertNull(placement.barReport(all))
    }
}
