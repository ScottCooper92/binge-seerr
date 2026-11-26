package com.cooper.binge.companion.seerr.discover.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CertificationEditsTest {
    private val ratings = listOf("G", "PG", "PG-13", "R")

    @Test
    fun `a tap sets the maximum and a second tap on it makes it exact, then clears it`() {
        assertEquals("PG" to null, tapCertification(ratings, null, null, "PG"))
        assertEquals("PG" to "PG", tapCertification(ratings, "PG", null, "PG"))
        assertEquals(null to null, tapCertification(ratings, "PG", "PG", "PG"))
    }

    @Test
    fun `a tap keeps a minimum set below it and drops one set above it`() {
        assertEquals("R" to "PG", tapCertification(ratings, "PG-13", "PG", "R"))
        assertEquals("G" to null, tapCertification(ratings, "PG-13", "PG-13", "G"))
        assertEquals("G" to null, tapCertification(ratings, null, "R", "G"))
    }

    @Test
    fun `a long press sets the minimum and keeps a maximum above it`() {
        assertEquals("R" to "PG", longPressCertification(ratings, "R", null, "PG"))
        assertEquals("PG" to "PG", longPressCertification(ratings, null, "PG", "PG"))
        assertEquals(null to null, longPressCertification(ratings, "PG", "PG", "PG"))
        assertEquals(null to "R", longPressCertification(ratings, "G", null, "R"))
    }

    @Test
    fun `ratings inside the range are in and the rest are out`() {
        assertNull(certificationIncluded(ratings, 0, null, null))
        assertEquals(listOf(true, true, false, false), ratings.indices.map { certificationIncluded(ratings, it, "PG", null) })
        assertEquals(listOf(false, true, true, true), ratings.indices.map { certificationIncluded(ratings, it, null, "PG") })
        assertEquals(listOf(false, true, true, false), ratings.indices.map { certificationIncluded(ratings, it, "PG-13", "PG") })
    }
}
