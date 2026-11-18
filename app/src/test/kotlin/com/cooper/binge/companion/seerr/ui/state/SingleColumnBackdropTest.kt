package com.cooper.binge.companion.seerr.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleColumnBackdropTest {
    @Test
    fun `the title's own backdrop is used when it has one`() {
        assertEquals("backdrop", singleColumnBackdrop("backdrop", "poster"))
    }

    @Test
    fun `the poster stands in for a missing backdrop`() {
        assertEquals("poster", singleColumnBackdrop(null, "poster"))
    }

    @Test
    fun `a title with neither has no image`() {
        assertNull(singleColumnBackdrop(null, null))
    }
}
