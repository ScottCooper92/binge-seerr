package com.cooper.binge.companion.seerr.auth

import com.binge.companion.kit.AesGcmSecretCipher
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The secrets already on users' devices were written by this app's own cipher before it moved to the kit's. The kit
 * promises the same blob, and a drift would sign everyone out, so this pins it from both sides.
 *
 * [OLD_BLOB] was made outside this code base, by the recipe the old class followed: AES-256-GCM, a 128-bit tag, the
 * 12-byte IV in front of the ciphertext, all Base64 with no line breaks. The Keystore key is stood in for by a fixed
 * software key, since the Keystore does not exist on the JVM.
 */
class StoredSecretFormatTest {
    private val key = SecretKeySpec(ByteArray(KEY_BYTES) { it.toByte() }, "AES")
    private val cipher = AesGcmSecretCipher { key }

    @Test
    fun `a blob written by the old cipher still decrypts`() {
        assertEquals(PLAINTEXT, cipher.decrypt(OLD_BLOB))
    }

    @Test
    fun `a blob written now can be read the way the old cipher read one`() {
        val blob = Base64.getDecoder().decode(cipher.encrypt(PLAINTEXT))
        val reader = Cipher.getInstance("AES/GCM/NoPadding")
        reader.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES)),
        )

        assertEquals(PLAINTEXT, String(reader.doFinal(blob.copyOfRange(IV_BYTES, blob.size)), Charsets.UTF_8))
    }

    private companion object {
        const val PLAINTEXT = "k3y-s3cret-value"
        const val OLD_BLOB = "ZGVmZ2hpamtsbW5vIyinSwraNexbFnKeuwkfmG5cp9MEH0DdTirvEdgI3eQ="
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
