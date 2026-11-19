package com.cooper.binge.companion.seerr.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A test that builds its rule with `createComposeRule()` skips the take-down in
 * [createSeerrComposeRule], and the cost lands on whichever paging test runs after it (#663).
 */
class ComposeRuleConventionTest {
    @Test
    fun `every compose test uses the module's rules rather than the bare ones`() {
        val offenders =
            File("src/test/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "ComposeRules.kt" }
                .filter { file -> DIRECT.containsMatchIn(file.readText()) }
                .map { it.name }
                .sorted()
                .toList()

        assertEquals("Use createSeerrComposeRule() or createSeerrAndroidComposeRule() in these files", emptyList<String>(), offenders)
    }

    /**
     * `createEmptyComposeRule()` hosts an activity that sets its own content, so it has no content of its own to take down. A host
     * built on it carries its own release: it drops focus before its activity goes, as the take-down does for the other rules.
     */
    @Test
    fun `every empty-rule host releases focus before its activity goes`() {
        val offenders =
            File("src/test/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "ComposeRules.kt" }
                .filter { file -> EMPTY.containsMatchIn(file.readText()) }
                .filterNot { file -> file.readText().contains("releaseFocus()") }
                .map { it.name }
                .sorted()
                .toList()

        assertEquals("These hosts use createEmptyComposeRule() without a releaseFocus()", emptyList<String>(), offenders)
    }

    private companion object {
        // The import, not the call: a use has to import it, and a comment naming it does not.
        val DIRECT = Regex("""^import androidx\.compose\.ui\.test\.junit4(\.v2)?\.create(Android)?ComposeRule$""", RegexOption.MULTILINE)
        val EMPTY = Regex("""^import androidx\.compose\.ui\.test\.junit4(\.v2)?\.createEmptyComposeRule$""", RegexOption.MULTILINE)
    }
}
