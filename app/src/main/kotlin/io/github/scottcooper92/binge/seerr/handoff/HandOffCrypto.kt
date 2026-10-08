package io.github.scottcooper92.binge.seerr.handoff

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val KEY_BYTES = 32
private const val NONCE_BYTES = 12
private const val TAG_BITS = 128
private const val HKDF_INFO = "seerr-tv-handoff-credentials-v1"
private const val PIN_INFO = "seerr-tv-handoff-pin-v1"
private const val PIN_DIGITS = 4
private const val PIN_MODULUS = 10_000L
private const val UINT_MASK = 0xFFFFFFFFL

/** The key as it travels in a link: 32 bytes as 43 URL-safe characters, no padding. */
internal val HAND_OFF_KEY_SHAPE = Regex("[A-Za-z0-9_-]{43}")

/**
 * What lets a phone seal credentials for one television and nobody else: 256 random bits, made when a hand-off
 * opens and shown only in the code on the TV's screen.
 *
 * The key is never sent over the network. It rides in the code's URL *fragment*, which a browser keeps to itself,
 * and from there into the app link on the phone. A listener that only reads plain HTTP, and anyone watching the
 * LAN, therefore sees the sealed bytes and not what opens them.
 */
class HandOffKey(
    private val bytes: ByteArray,
) {
    init {
        require(bytes.size == KEY_BYTES) { "A hand-off key is $KEY_BYTES bytes" }
    }

    /** The key as it travels in a link. */
    fun encoded(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /**
     * [plaintext] sealed with AES-256-GCM under a key derived from this one (HKDF-SHA256), with [context] bound in as
     * associated data so a message sealed for one hand-off cannot be replayed into another. Nonce first, then the
     * ciphertext and tag, as URL-safe text.
     */
    fun seal(
        plaintext: ByteArray,
        context: String,
        random: SecureRandom = SecureRandom(),
    ): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived(), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce + cipher.doFinal(plaintext))
    }

    /** What [sealed] holds, or null if it was not sealed with this key for this [context], or has been altered. */
    fun open(
        sealed: String,
        context: String,
    ): ByteArray? =
        try {
            val raw = Base64.getUrlDecoder().decode(sealed)
            require(raw.size > NONCE_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived(), "AES"), GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, NONCE_BYTES)))
            cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
            cipher.doFinal(raw, NONCE_BYTES, raw.size - NONCE_BYTES)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /**
     * The four digits the television shows beside its code, and the phone asks for before it does anything with a link
     * (#803). The digits come from this key, so the phone checks them itself: a link someone else wrote carries their key,
     * whose PIN won't be the one on the user's screen. HMAC-SHA256 of a fixed label under the key, its first four bytes
     * as an unsigned number, modulo 10,000. The small bias towards low PINs costs nothing here: guessing is only a
     * 1-in-10,000 chance, and nobody but the person looking at the TV can see what to aim for.
     */
    fun pin(): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(bytes, "HmacSHA256")) }
        val digest = mac.doFinal(PIN_INFO.toByteArray(Charsets.UTF_8))
        val number = ByteBuffer.wrap(digest, 0, Int.SIZE_BYTES).int.toLong() and UINT_MASK
        return (number % PIN_MODULUS).toString().padStart(PIN_DIGITS, '0')
    }

    /** HKDF-SHA256 with no salt, extracting then expanding one block: the key AES uses is never the one in the link. */
    private fun derived(): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(KEY_BYTES), "HmacSHA256")) }
        val prk = extract.doFinal(bytes)
        val expand = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
        return expand.doFinal(HKDF_INFO.toByteArray(Charsets.UTF_8) + byteArrayOf(1))
    }

    companion object {
        fun generate(random: SecureRandom = SecureRandom()): HandOffKey = HandOffKey(ByteArray(KEY_BYTES).also(random::nextBytes))

        /** The key a link carries, or null for anything that is not 43 URL-safe characters. */
        fun parse(encoded: String?): HandOffKey? =
            encoded
                ?.takeIf { HAND_OFF_KEY_SHAPE.matches(it) }
                ?.let { HandOffKey(Base64.getUrlDecoder().decode(it)) }
    }
}
