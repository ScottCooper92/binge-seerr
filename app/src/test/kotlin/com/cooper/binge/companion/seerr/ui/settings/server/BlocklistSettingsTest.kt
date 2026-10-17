package com.cooper.binge.companion.seerr.ui.settings.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistSettingsTest {
    @Test
    fun `the tag limit is a whole number from 0 up, so a stored value past the web client's 250 still saves`() {
        assertTrue(BlocklistSettings(tagsLimit = "0").valid)
        assertTrue(BlocklistSettings(tagsLimit = "250").valid)
        assertTrue(BlocklistSettings(tagsLimit = "300").valid)
        assertFalse(BlocklistSettings(tagsLimit = "-1").valid)
        assertFalse(BlocklistSettings(tagsLimit = "").valid)
    }

    @Test
    fun `a stored limit past 250 leaves the general page savable`() {
        assertTrue(ServerGeneralSettings(blocklist = BlocklistSettings(tagsLimit = "300")).valid)
    }

    @Test
    fun `tags are read as ids, skipping anything that is not one`() {
        assertEquals(listOf(9951, 210024), BlocklistSettings(tags = "9951, 210024,,x").tagIds)
        assertEquals(emptyList<Int>(), BlocklistSettings(tags = "").tagIds)
    }
}
