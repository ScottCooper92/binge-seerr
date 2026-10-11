package com.cooper.binge.companion.seerr.util

import com.cooper.binge.companion.seerr.auth.SecretCipher

/**
 * A cipher whose ciphertext differs from its plaintext, for a test that must tell the stored blob apart from what was
 * stored. A test that only needs a cipher to exist uses [PlainCipher] (#1210).
 */
internal object ReversingCipher : SecretCipher {
    override fun encrypt(plaintext: String): String = plaintext.reversed()

    override fun decrypt(ciphertext: String): String = ciphertext.reversed()
}
