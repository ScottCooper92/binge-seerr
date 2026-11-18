package com.cooper.binge.companion.seerr.ui.hub

import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags
import org.junit.Assert.assertEquals
import org.junit.Test

class BingeStatusTest {
    @Test
    fun `nothing is said about Binge while promotion is off`() {
        assertEquals(BingeStatus.Hidden, bingeStatusOf(installed = false, connected = false, flags = Flags()))
        assertEquals(BingeStatus.Hidden, bingeStatusOf(installed = true, connected = true, flags = Flags()))
    }

    @Test
    fun `promotion gives the three states`() {
        assertEquals(
            BingeStatus.NotInstalled,
            bingeStatusOf(
                installed = false,
                connected = true,
                flags =
                    Flags.of(
                        SharedFlags.BingePromotion to true,
                        SharedFlags.ReviewPrompt to true,
                    ),
            ),
        )
        assertEquals(
            BingeStatus.NotConnected,
            bingeStatusOf(
                installed = true,
                connected = false,
                flags =
                    Flags.of(
                        SharedFlags.BingePromotion to true,
                        SharedFlags.ReviewPrompt to true,
                    ),
            ),
        )
        assertEquals(
            BingeStatus.Connected,
            bingeStatusOf(
                installed = true,
                connected = true,
                flags =
                    Flags.of(
                        SharedFlags.BingePromotion to true,
                        SharedFlags.ReviewPrompt to true,
                    ),
            ),
        )
    }
}
