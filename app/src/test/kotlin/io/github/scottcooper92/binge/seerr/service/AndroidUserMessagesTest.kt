package io.github.scottcooper92.binge.seerr.service

import androidx.test.core.app.ApplicationProvider
import io.github.scottcooper92.binge.seerr.seerr.UserFacingRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Service's sentences are this app's own strings, in the device's locale (binge-companions#177). */
@RunWith(RobolectricTestRunner::class)
class AndroidUserMessagesTest {
    private val messages = AndroidUserMessages(ApplicationProvider.getApplicationContext())

    @Test
    @Config(qualifiers = "en-rGB")
    fun `a sentence is in the device's language and names the locale it is in`() {
        val quota = messages.sentence(UserFacingRefusal.QuotaSpent)
        assertEquals("en", quota.locale)
        assertTrue(quota.message.contains("request limit"))
    }

    @Test
    @Config(qualifiers = "es")
    fun `a Spanish device gets the Spanish sentence`() {
        val blocklisted = messages.sentence(UserFacingRefusal.Blocklisted)
        assertEquals("es", blocklisted.locale)
        assertTrue(blocklisted.message.contains("lista de bloqueo"))
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    fun `a device in an unshipped language gets English, tagged as English`() {
        val quota = messages.sentence(UserFacingRefusal.QuotaSpent)
        assertEquals("en", quota.locale)
        assertTrue(quota.message.contains("request limit"))
    }
}
