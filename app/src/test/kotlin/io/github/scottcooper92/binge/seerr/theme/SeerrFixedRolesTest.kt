package io.github.scottcooper92.binge.seerr.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.binge.designsystem.theme.DarkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixed roles carry the design system's big fills, so this app's brand sets all four. They are
 * theme-independent, they are not Binge's amber, and their ink stays legible on both fills.
 */
class SeerrFixedRolesTest {
    @Test
    fun `the fixed roles are the same in both themes`() {
        val light = SeerrLightColorScheme
        val dark = SeerrDarkColorScheme
        assertEquals(light.primaryFixed, dark.primaryFixed)
        assertEquals(light.primaryFixedDim, dark.primaryFixedDim)
        assertEquals(light.onPrimaryFixed, dark.onPrimaryFixed)
        assertEquals(light.onPrimaryFixedVariant, dark.onPrimaryFixedVariant)
    }

    @Test
    fun `the fixed roles are this app's, not Binge's`() {
        assertNotEquals(DarkColorScheme.primaryFixed, SeerrDarkColorScheme.primaryFixed)
        assertNotEquals(DarkColorScheme.primaryFixedDim, SeerrDarkColorScheme.primaryFixedDim)
        assertNotEquals(DarkColorScheme.onPrimaryFixed, SeerrDarkColorScheme.onPrimaryFixed)
        assertNotEquals(DarkColorScheme.onPrimaryFixedVariant, SeerrDarkColorScheme.onPrimaryFixedVariant)
    }

    @Test
    fun `onPrimaryFixed clears 4_5 to 1 on both fixed fills`() {
        val scheme = SeerrDarkColorScheme
        for (fill in listOf(scheme.primaryFixed, scheme.primaryFixedDim)) {
            val ratio = contrast(scheme.onPrimaryFixed, fill)
            assertTrue("onPrimaryFixed on $fill is $ratio:1", ratio >= MIN_TEXT_CONTRAST)
        }
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (high, low) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (high + LUMINANCE_OFFSET) / (low + LUMINANCE_OFFSET)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5f
        const val LUMINANCE_OFFSET = 0.05f
    }
}
