package com.cooper.binge.companion.seerr.auth

import com.cooper.homelab.core.server.connection.KeystoreSecretCipher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.Security
import java.util.Base64
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The secrets already on users' devices were written by this app's own cipher before it moved to the shared library's
 * `KeystoreSecretCipher`, the class that ships. The library promises the same blob, and a drift would sign everyone out,
 * so this pins it from both sides.
 *
 * [OLD_BLOB] was made outside this code base, by the recipe the old class followed: AES-256-GCM, a 128-bit tag, the
 * 12-byte IV in front of the ciphertext, all Base64 with no line breaks. Robolectric has no
 * `AndroidKeyStore`, so a stand-in provider holds a fixed software key under the app's alias.
 */
@RunWith(RobolectricTestRunner::class)
class StoredSecretFormatTest {
    private val key = SecretKeySpec(ByteArray(KEY_BYTES) { it.toByte() }, "AES")
    private val cipher = KeystoreSecretCipher(ALIAS)

    @Before
    fun install() {
        keys.clear()
        keys[ALIAS] = key
        Security.removeProvider(PROVIDER)
        Security.addProvider(
            object : Provider(PROVIDER, 1.0, "test stand-in") {
                init {
                    put("KeyStore.$PROVIDER", FakeKeyStoreSpi::class.java.name)
                }
            },
        )
    }

    @After
    fun remove() {
        Security.removeProvider(PROVIDER)
    }

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

    /** A [KeyStoreSpi] over [keys], with only the lookups the cipher makes. */
    class FakeKeyStoreSpi : KeyStoreSpi() {
        override fun engineGetKey(
            alias: String,
            password: CharArray?,
        ): Key? = keys[alias]

        override fun engineGetEntry(
            alias: String,
            protParam: KeyStore.ProtectionParameter?,
        ): KeyStore.Entry? = keys[alias]?.let { KeyStore.SecretKeyEntry(it) }

        override fun engineContainsAlias(alias: String): Boolean = alias in keys

        override fun engineIsKeyEntry(alias: String): Boolean = alias in keys

        override fun engineIsCertificateEntry(alias: String): Boolean = false

        override fun engineLoad(
            stream: InputStream?,
            password: CharArray?,
        ) = Unit

        override fun engineAliases() = Collections.enumeration(keys.keys.toList())

        override fun engineSize(): Int = keys.size

        override fun engineGetCertificateChain(alias: String) = null

        override fun engineGetCertificate(alias: String) = null

        override fun engineGetCreationDate(alias: String) = null

        override fun engineSetKeyEntry(
            alias: String,
            key: Key?,
            password: CharArray?,
            chain: Array<out java.security.cert.Certificate>?,
        ) = Unit

        override fun engineSetKeyEntry(
            alias: String,
            key: ByteArray?,
            chain: Array<out java.security.cert.Certificate>?,
        ) = Unit

        override fun engineSetCertificateEntry(
            alias: String,
            cert: java.security.cert.Certificate?,
        ) = Unit

        override fun engineDeleteEntry(alias: String) = Unit

        override fun engineGetCertificateAlias(cert: java.security.cert.Certificate?): String? = null

        override fun engineStore(
            stream: java.io.OutputStream?,
            password: CharArray?,
        ) = Unit
    }

    private companion object {
        val keys: MutableMap<String, SecretKey> = Collections.synchronizedMap(linkedMapOf())
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "stored_secret_format_test"
        const val PLAINTEXT = "k3y-s3cret-value"
        const val OLD_BLOB = "ZGVmZ2hpamtsbW5vIyinSwraNexbFnKeuwkfmG5cp9MEH0DdTirvEdgI3eQ="
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
