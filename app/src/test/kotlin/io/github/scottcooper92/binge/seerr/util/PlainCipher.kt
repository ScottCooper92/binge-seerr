package io.github.scottcooper92.binge.seerr.util

import io.github.scottcooper92.binge.seerr.auth.SecretCipher

/**
 * The cipher every test that stores credentials uses: it hands back what it is given, so a test reads what it wrote
 * without a Keystore. A test that must tell the ciphertext from the plaintext keeps its own reversing fake (#1162).
 */
internal object PlainCipher : SecretCipher {
    override fun encrypt(plaintext: String): String = plaintext

    override fun decrypt(ciphertext: String): String = ciphertext
}
