package com.cooper.binge.companion.seerr.ui.grid

import org.junit.Assert.assertEquals
import org.junit.Test

class CountryNameTest {
    @Test
    fun `a subdivision code is its country's name with the subdivision after it`() {
        assertEquals("${countryName("CA")} (QC)", countryName("CA-QC"))
    }

    @Test
    fun `a code the platform cannot name stands for itself`() {
        assertEquals("!!", countryName("!!"))
        assertEquals("", countryName(""))
    }
}
