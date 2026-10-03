import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Stamps a small module's strings with `updateTranslationHashes`, then runs the check against them. */
class CheckTranslationStalenessTaskTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var project: Project

    @Before
    fun stampHashes() {
        project = ProjectBuilder.builder().withProjectDir(folder.root).build()
        strings("values", "greeting" to "Hello", "farewell" to "Goodbye")
        strings("values-es", "greeting" to "Hola", "farewell" to "Adiós")
        task("updateTranslationHashes", rewrite = true).check()
    }

    @Test
    fun `passes when every translated source string matches its hash`() {
        task("checkTranslationStaleness", rewrite = false).check()
    }

    @Test
    fun `fails and names a source string reworded since it was stamped`() {
        strings("values", "greeting" to "Hi there", "farewell" to "Goodbye")

        val failure =
            assertThrows(GradleException::class.java) {
                task("checkTranslationStaleness", rewrite = false).check()
            }

        assertTrue(failure.message, failure.message!!.contains("app:greeting"))
        assertFalse(failure.message, failure.message!!.contains("app:farewell"))
    }

    private fun task(
        name: String,
        rewrite: Boolean,
    ) = project.tasks.register(name, CheckTranslationStalenessTask::class.java) {
        stringFiles.from(project.fileTree(folder.root) { include("app/src/*/res/values*/strings.xml") })
        hashFile.set(File(folder.root, "translation-hashes.txt"))
        repoRoot.set(folder.root)
        this.rewrite.set(rewrite)
    }.get()

    private fun strings(
        directory: String,
        vararg entries: Pair<String, String>,
    ): File =
        File(folder.root, "app/src/main/res/$directory/strings.xml").apply {
            parentFile.mkdirs()
            writeText(
                entries.joinToString("\n", "<resources>\n", "\n</resources>\n") { (name, value) ->
                    """    <string name="$name">$value</string>"""
                },
            )
        }
}
