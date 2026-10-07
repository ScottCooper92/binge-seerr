package io.github.scottcooper92.binge.seerr.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The seal a phone puts on credentials: only the key in the code opens it, for the one hand-off it was made for. */
class HandOffCryptoTest {
    private val key = HandOffKey.generate()
    private val message = """{"mode":"Local","email":"ana@example.com","password":"correct horse"}""".toByteArray()

    @Test
    fun `a message opens with the key and the context it was sealed for`() {
        val sealed = key.seal(message, context = "k7m2pqx4")

        assertEquals(message.decodeToString(), key.open(sealed, context = "k7m2pqx4")?.decodeToString())
    }

    @Test
    fun `the sealed text is URL-safe and says nothing of what it holds`() {
        val sealed = key.seal(message, context = "k7m2pqx4")

        assertTrue(Regex("[A-Za-z0-9_-]+").matches(sealed))
        assertFalse(sealed.contains("correct"))
        assertFalse(sealed.contains("ana"))
    }

    @Test
    fun `the same message is sealed differently each time`() {
        assertNotEquals(key.seal(message, "k7m2pqx4"), key.seal(message, "k7m2pqx4"))
    }

    @Test
    fun `another key, another context or a changed byte opens nothing`() {
        val sealed = key.seal(message, context = "k7m2pqx4")

        assertNull(HandOffKey.generate().open(sealed, "k7m2pqx4"))
        assertNull(key.open(sealed, "other000"))
        val raw =
            java.util.Base64
                .getUrlDecoder()
                .decode(sealed)
        raw[raw.size - 1] = (raw.last().toInt() xor 1).toByte()
        assertNull(
            key.open(
                java.util.Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(raw),
                "k7m2pqx4",
            ),
        )
    }

    @Test
    fun `text that is not a sealed message opens as nothing, without throwing`() {
        listOf("", "!!!", "AAAA", "A".repeat(10), "A".repeat(200)).forEach { assertNull(it, key.open(it, "k7m2pqx4")) }
    }

    @Test
    fun `a key travels as 43 URL-safe characters and reads back as the same key`() {
        val encoded = key.encoded()

        assertEquals(43, encoded.length)
        val sealed = key.seal(message, "k7m2pqx4")
        assertNotNull(HandOffKey.parse(encoded)?.open(sealed, "k7m2pqx4"))
    }

    @Test
    fun `anything but 43 URL-safe characters is not a key`() {
        listOf(null, "", "short", "A".repeat(42), "A".repeat(44), "A".repeat(42) + "=", "A".repeat(42) + "+").forEach {
            assertNull(it, HandOffKey.parse(it))
        }
    }

    @Test
    fun `credentials print their mode and never a secret`() {
        val credentials = HandOffCredentials(mode = "Local", email = "ana@example.com", password = "correct horse", apiKey = "abc123")

        assertEquals("HandOffCredentials(mode=Local)", credentials.toString())
    }
}
