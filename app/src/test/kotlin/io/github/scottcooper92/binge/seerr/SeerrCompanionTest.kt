package io.github.scottcooper92.binge.seerr

import org.junit.Assert.assertEquals
import org.junit.Test

class SeerrCompanionTest {
    @Test
    fun `serves REQUEST contract v1`() {
        assertEquals(1, SeerrCompanion.REQUEST_CONTRACT_VERSION)
    }
}
