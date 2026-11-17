package com.cooper.binge.companion.seerr.auth

import com.cooper.binge.companion.seerr.seerr.insecurePublicHostOrNull
import com.cooper.homelab.core.server.network.DataStoreCleartextConsent
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The consent store itself is the shared library's; what is this app's is how a saved address becomes the host it grandfathers. */
class CleartextConsentTest {
    private fun consent(saved: String?) = DataStoreCleartextConsent(InMemoryDataStore()) { saved?.insecurePublicHostOrNull() }

    @Test
    fun `a connection saved over public http before the opt-in existed is grandfathered in`() =
        runTest {
            assertTrue(consent("http://seerr.example.com:5055/").allows("seerr.example.com"))
        }

    @Test
    fun `a saved https or lan connection grandfathers nothing`() =
        runTest {
            listOf("https://seerr.example.com/", "http://seerr.lan:5055/").forEach { saved ->
                val consent = consent(saved)
                assertFalse(saved, consent.allows("seerr.example.com"))
                assertFalse(saved, consent.allows("seerr.lan"))
            }
        }
}
