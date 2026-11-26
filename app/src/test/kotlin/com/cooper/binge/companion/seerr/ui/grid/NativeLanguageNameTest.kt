package com.cooper.binge.companion.seerr.ui.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeLanguageNameTest {
    @Test
    fun `a language is written in itself with a capital`() {
        assertEquals("Français", nativeLanguageName("fr"))
        assertEquals("Español", nativeLanguageName("es"))
        assertEquals("日本語", nativeLanguageName("ja"))
    }

    @Test
    fun `a code the platform cannot name has no native name`() {
        assertNull(nativeLanguageName("xx"))
    }
}
