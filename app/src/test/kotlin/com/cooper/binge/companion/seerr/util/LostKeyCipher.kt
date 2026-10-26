package com.cooper.binge.companion.seerr.util

import com.cooper.binge.companion.seerr.auth.SecretCipher

/** A cipher whose key is gone: it still encrypts, but nothing it stored decrypts again, as after a Keystore wipe. */
internal object LostKeyCipher : SecretCipher {
    override fun encrypt(plaintext: String): String = plaintext.reversed()

    override fun decrypt(ciphertext: String): String? = null
}
