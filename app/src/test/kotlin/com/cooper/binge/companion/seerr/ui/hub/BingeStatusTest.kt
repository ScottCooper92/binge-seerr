package com.cooper.binge.companion.seerr.ui.hub

import com.cooper.binge.companion.seerr.flags.Flags
import org.junit.Assert.assertEquals
import org.junit.Test

class BingeStatusTest {
    private val promotionOnly = Flags(bingePromotion = true)
    private val companionsOnly = Flags(requestCompanions = true)

    @Test
    fun `nothing is said about Binge while promotion is off`() {
        listOf(Flags(), companionsOnly).forEach { flags ->
            assertEquals(BingeStatus.Hidden, bingeStatusOf(installed = false, connected = false, flags = flags))
            assertEquals(BingeStatus.Hidden, bingeStatusOf(installed = true, connected = true, flags = flags))
        }
    }

    @Test
    fun `promotion alone offers the install and nothing about connecting`() {
        assertEquals(BingeStatus.NotInstalled, bingeStatusOf(installed = false, connected = false, flags = promotionOnly))
        assertEquals(BingeStatus.Hidden, bingeStatusOf(installed = true, connected = false, flags = promotionOnly))
        assertEquals(BingeStatus.Hidden, bingeStatusOf(installed = true, connected = true, flags = promotionOnly))
    }

    @Test
    fun `both flags give the three states`() {
        assertEquals(BingeStatus.NotInstalled, bingeStatusOf(installed = false, connected = true, flags = Flags.AllOn))
        assertEquals(BingeStatus.NotConnected, bingeStatusOf(installed = true, connected = false, flags = Flags.AllOn))
        assertEquals(BingeStatus.Connected, bingeStatusOf(installed = true, connected = true, flags = Flags.AllOn))
    }
}
