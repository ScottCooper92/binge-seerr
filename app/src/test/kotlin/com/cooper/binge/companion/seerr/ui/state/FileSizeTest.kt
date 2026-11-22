package com.cooper.binge.companion.seerr.ui.state

import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `formatFileSize` is the platform's formatter, so a size reads as the device would write it, and a negative one reads as zero. */
@RunWith(RobolectricTestRunner::class)
class FileSizeTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun format(vararg sizes: Long): List<String> {
        val out = mutableListOf<String>()
        rule.setContent { BingeExpressiveTheme { sizes.forEach { out += formatFileSize(it) } } }
        return out
    }

    @Test
    fun `a size reads in the platform's units`() {
        assertEquals(listOf("0 B", "1.02 kB", "1.02 kB", "1.54 GB", "5.00 PB"), format(0, 1023, 1024, 1_536_000_000, 5_000_000_000_000_000))
    }

    @Test
    fun `a negative size reads as zero`() {
        val (negative, zero) = format(-5, 0)
        assertEquals(zero, negative)
    }

    @Test
    @Config(qualifiers = "es")
    fun `the decimal separator follows the app's language`() {
        assertEquals(listOf("1,54 GB"), format(1_536_000_000))
    }
}
