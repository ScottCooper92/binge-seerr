package io.github.scottcooper92.binge.seerr.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A test that builds its rule with `createComposeRule()` skips the take-down in
 * [createSeerrComposeRule], and the cost lands on whichever paging test runs after it (#663).
 */
class ComposeRuleConventionTest {
    @Test
    fun `every compose test uses the module's rule rather than createComposeRule`() {
        val offenders =
            File("src/test/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "ComposeRules.kt" }
                .filter { file -> DIRECT.containsMatchIn(file.readText()) }
                .map { it.name }
                .sorted()
                .toList()

        assertEquals("Use createSeerrComposeRule() in these files", emptyList<String>(), offenders)
    }

    private companion object {
        // The import, not the call: a use has to import it, and a comment naming it does not.
        val DIRECT = Regex("""^import androidx\.compose\.ui\.test\.junit4(\.v2)?\.createComposeRule$""", RegexOption.MULTILINE)
    }
}
