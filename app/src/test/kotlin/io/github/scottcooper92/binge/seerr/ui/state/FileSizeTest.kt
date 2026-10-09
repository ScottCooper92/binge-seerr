package io.github.scottcooper92.binge.seerr.ui.state

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** `formatFileSize`: one decimal from megabytes up, none below, and never a negative or a unit past terabytes. */
class FileSizeTest {
    private val original = Locale.getDefault()

    @Before
    fun pinLocale() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun `bytes and kilobytes carry no decimal`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("1023 B", formatFileSize(1023))
        assertEquals("1 KB", formatFileSize(1024))
    }

    @Test
    fun `megabytes and up carry one decimal`() {
        assertEquals("1.0 MB", formatFileSize(1024L * 1024))
        assertEquals("1.4 GB", formatFileSize(1_536_000_000))
    }

    @Test
    fun `a negative size reads as zero`() {
        assertEquals("0 B", formatFileSize(-5))
    }

    @Test
    fun `terabytes are the largest unit, so a bigger size stays in terabytes`() {
        assertEquals("5120.0 TB", formatFileSize(5L * 1024 * 1024 * 1024 * 1024 * 1024))
    }

    @Test
    fun `the decimal separator follows the locale`() {
        Locale.setDefault(Locale.forLanguageTag("es"))

        assertEquals("1,4 GB", formatFileSize(1_536_000_000))
    }
}
