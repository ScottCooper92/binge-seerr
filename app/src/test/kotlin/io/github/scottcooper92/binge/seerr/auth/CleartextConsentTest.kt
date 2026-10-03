package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextConsentTest {
    @Test
    fun `a granted host is allowed and no other is`() =
        runTest {
            val consent = DataStoreCleartextConsent(InMemoryDataStore()) { null }

            consent.grant("seerr.example.com")

            assertTrue(consent.allows("seerr.example.com"))
            assertFalse(consent.allows("other.example.com"))
        }

    @Test
    fun `retaining the saved server forgets the rest, and retaining none forgets everything`() =
        runTest {
            val consent = DataStoreCleartextConsent(InMemoryDataStore()) { null }
            consent.grant("seerr.example.com")
            consent.grant("abandoned.example.com")

            consent.retainOnly("seerr.example.com")
            assertTrue(consent.allows("seerr.example.com"))
            assertFalse(consent.allows("abandoned.example.com"))

            consent.retainOnly(null)
            assertFalse(consent.allows("seerr.example.com"))
        }

    @Test
    fun `a connection saved over public http before the opt-in existed is grandfathered in, once`() =
        runTest {
            var reads = 0
            val consent =
                DataStoreCleartextConsent(InMemoryDataStore()) {
                    reads++
                    "http://seerr.example.com:5055/"
                }

            assertTrue(consent.allows("seerr.example.com"))
            consent.retainOnly(null)

            // Seeded once: after a disconnect, the old saved server does not come back.
            assertFalse(consent.allows("seerr.example.com"))
            assertEquals(1, reads)
        }

    @Test
    fun `a saved https or lan connection grandfathers nothing`() =
        runTest {
            listOf("https://seerr.example.com/", "http://seerr.lan:5055/").forEach { saved ->
                val consent = DataStoreCleartextConsent(InMemoryDataStore()) { saved }
                assertFalse(saved, consent.allows("seerr.example.com"))
                assertFalse(saved, consent.allows("seerr.lan"))
            }
        }

    @Test
    fun `a grant before the first read still grandfathers the saved server`() =
        runTest {
            val consent = DataStoreCleartextConsent(InMemoryDataStore()) { "http://seerr.example.com/" }

            consent.grant("new.example.com")

            assertTrue(consent.allows("seerr.example.com"))
            assertTrue(consent.allows("new.example.com"))
        }
}
