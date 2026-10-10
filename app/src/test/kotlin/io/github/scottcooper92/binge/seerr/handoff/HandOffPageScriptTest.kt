package io.github.scottcooper92.binge.seerr.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * The page script seals credentials itself, since a plain-HTTP LAN page has no Web Crypto (#772). These hold its sealing
 * to the TV's: a vector the script produced opens with [HandOffKey], and, where Node is installed, the script as
 * shipped still produces that vector and the TV's associated data (#1112).
 */
class HandOffPageScriptTest {
    @Test
    fun `credentials the page script sealed open with the TV's key, for that token only`() {
        val key = checkNotNull(HandOffKey.parse(VECTOR_KEY))

        assertEquals(VECTOR_PLAINTEXT, key.open(VECTOR_SEALED, context = VECTOR_TOKEN)?.decodeToString())
        assertNull(key.open(VECTOR_SEALED, context = "another1"))
    }

    @Test
    fun `the shipped page script still seals the vector byte for byte`() {
        val nonce = (0 until NONCE_BYTES).joinToString(",") { (NONCE_START + it).toString() }
        val sealed =
            runSealHalf(
                "Seal.seal(${quote(VECTOR_KEY)},${quote(VECTOR_TOKEN)},${quote(VECTOR_PLAINTEXT)},new Uint8Array([$nonce]))",
            )

        assertEquals(VECTOR_SEALED, sealed)
    }

    /** #1112: the sign-in form's associated data is the script's own, and has to be byte for byte the TV's. */
    @Test
    fun `the page script builds the same associated data as the TV`() {
        val context = runSealHalf("Seal.context(${quote(VECTOR_TOKEN)},${quote(VECTOR_ADDRESS)})")

        assertEquals(HandOffKey.context(VECTOR_TOKEN, VECTOR_ADDRESS), context)
    }

    @Test
    fun `credentials sealed as the sign-in form seals them open on the TV for that address only`() {
        val sealed =
            runSealHalf(
                "Seal.seal(${quote(VECTOR_KEY)},Seal.context(${quote(VECTOR_TOKEN)},${quote(VECTOR_ADDRESS)})," +
                    "${quote(VECTOR_PLAINTEXT)})",
            )
        val key = checkNotNull(HandOffKey.parse(VECTOR_KEY))

        assertEquals(VECTOR_PLAINTEXT, key.open(checkNotNull(sealed), HandOffKey.context(VECTOR_TOKEN, VECTOR_ADDRESS))?.decodeToString())
        assertNull(key.open(sealed, HandOffKey.context(VECTOR_TOKEN, "http://192.168.1.66:5055/")))
    }

    /** The controller drives a page, which Node has none of, so the half it runs is pinned to calling `Seal.context`. */
    @Test
    fun `the sign-in form seals for the address it shows, through the script's own context`() {
        assertTrue(PAGE_SCRIPT.contains("""Seal.seal(K,Seal.context(T,f.getAttribute("data-address")),"""))
    }

    /**
     * Runs the sealing half of the script under Node and returns what [expression] prints; skips the test where Node is
     * not installed. The rest of the script drives a page, which Node has none of.
     */
    private fun runSealHalf(expression: String): String? {
        val node = nodeOrNull()
        assumeTrue("Node is not installed here", node != null)
        val program = PAGE_SCRIPT.substringBefore(CONTROLLER_START) + "})();\nprocess.stdout.write($expression);"
        val file = File.createTempFile("seal", ".js").apply { writeText(program) }
        return try {
            val process = ProcessBuilder(checkNotNull(node), file.path).redirectErrorStream(true).start()
            check(process.waitFor(NODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Node did not finish" }
            process.inputStream.readBytes().decodeToString()
        } finally {
            file.delete()
        }
    }

    private fun nodeOrNull(): String? =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .map { File(it, "node") }
            .firstOrNull { it.canExecute() }
            ?.path

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        /** Bytes 0..31, as the link carries them. */
        val VECTOR_KEY: String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { it.toByte() })
        const val VECTOR_TOKEN = "k7m2pqx4"
        const val VECTOR_ADDRESS = "http://192.168.1.10:5055/"
        const val VECTOR_PLAINTEXT = """{"mode":"Local","email":"tv@example.com","password":"pässwörd"}"""

        /** What the page script sealed [VECTOR_PLAINTEXT] as, with the nonce 200..211, when it was written. */
        const val VECTOR_SEALED =
            "yMnKy8zNzs_Q0dLTorfaLA802HaWqJt4damcnjiTYWMIxtjPHwyJ3ewj9ZFy_89JozcAB2ljvsR1i1j1zYpXNcyCB58RLJGBAof6uE0Dc6ClsfmOD7RPKjsqD8HB"
        const val NONCE_BYTES = 12
        const val NONCE_START = 200
        const val CONTROLLER_START = "})();\n(function(){"
        const val NODE_TIMEOUT_SECONDS = 20L
    }
}
