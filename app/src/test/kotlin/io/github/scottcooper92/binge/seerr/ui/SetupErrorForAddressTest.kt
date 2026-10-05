package io.github.scottcooper92.binge.seerr.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The television's error for an address a phone sent that it could not reach. */
class SetupErrorForAddressTest {
    @Test
    fun `an unreachable address a phone sent says when it is not local`() {
        listOf("http://100.101.102.103:5055/", "http://[fd7a:115c:a1e0::1]:5055/", "http://8.8.8.8/").forEach {
            assertEquals(it, SetupError.UnreachableNotLocal, SetupError.Unreachable.forAddress(it, received = true))
        }
    }

    @Test
    fun `a local, named or typed address keeps the plain error, and so does any other failure`() {
        assertEquals(SetupError.Unreachable, SetupError.Unreachable.forAddress("http://192.168.1.10:5055/", received = true))
        assertEquals(SetupError.Unreachable, SetupError.Unreachable.forAddress("https://seerr.example.com/", received = true))
        assertEquals(SetupError.Unreachable, SetupError.Unreachable.forAddress("http://100.101.102.103:5055/", received = false))
        assertEquals(SetupError.NotSeerr, SetupError.NotSeerr.forAddress("http://100.101.102.103:5055/", received = true))
    }
}
