package com.cooper.binge.companion.seerr.ui.requests

import org.junit.Assert.assertEquals
import org.junit.Test

class IssueTypeIconTest {
    @Test
    fun `each kind of issue has its own glyph`() {
        val icons = IssueType.entries.map { it.icon() }

        assertEquals(IssueType.entries.size, icons.toSet().size)
    }
}
