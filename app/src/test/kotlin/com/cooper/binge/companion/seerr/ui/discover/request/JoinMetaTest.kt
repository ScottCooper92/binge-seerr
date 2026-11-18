package com.cooper.binge.companion.seerr.ui.discover.request

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoinMetaTest {
    @Test
    fun `the parts that exist are joined`() {
        assertEquals("1999 · 2 h", joinMeta(listOf("1999", null, "2 h"), " · "))
    }

    @Test
    fun `no parts is no line, not an empty one`() {
        assertNull(joinMeta(listOf(null, ""), " · "))
        assertNull(joinMeta(emptyList(), " · "))
    }
}
