package com.cooper.binge.companion.seerr.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPollingDefaultTest {
    @Test
    fun `a shipped build runs the shared poll whatever the override file holds`() {
        assertTrue(sharedPollingOn(debug = false, override = null))
        assertTrue(sharedPollingOn(debug = false, override = true))
        assertTrue(sharedPollingOn(debug = false, override = false))
    }

    @Test
    fun `a debug build runs the shared poll unless the override says off`() {
        assertTrue(sharedPollingOn(debug = true, override = null))
        assertTrue(sharedPollingOn(debug = true, override = true))
        assertFalse(sharedPollingOn(debug = true, override = false))
    }
}
