package io.github.scottcooper92.binge.seerr.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * The page script seals credentials itself, since a plain-HTTP LAN page has no Web Crypto (#772). These hold its sealing
 * to the TV's: a vector the script produced opens with [HandOffKey], and, where Node is installed, the script as
 * shipped still produces that vector.
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
        val node = nodeOrNull()
        assumeTrue("Node is not installed here", node != null)
        // The sealing half only: the rest of the script drives a page, which Node has none of.
        val seal = PAGE_SCRIPT.substringBefore(CONTROLLER_START) + "})();"
        val nonce = (0 until NONCE_BYTES).joinToString(",") { (NONCE_START + it).toString() }
        val program =
            seal +
                "\nprocess.stdout.write(Seal.seal(${quote(VECTOR_KEY)},${quote(VECTOR_TOKEN)}," +
                "${quote(VECTOR_PLAINTEXT)},new Uint8Array([$nonce])));"
        val file = File.createTempFile("seal", ".js").apply { writeText(program) }
        try {
            val process = ProcessBuilder(checkNotNull(node), file.path).redirectErrorStream(true).start()
            check(process.waitFor(NODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Node did not finish" }
            assertEquals(VECTOR_SEALED, process.inputStream.readBytes().decodeToString())
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
