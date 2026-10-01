package io.github.scottcooper92.binge.seerr.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * That every TV focus seed has a frame that passes one (#538).
 *
 * On TV, focus is a parameter rather than a runtime state, so the focused look of a component is
 * checked only if some frame seeds it. `validateDebugScreenshotTest` can say a frame changed, never
 * that one is absent. This is that check.
 *
 * It is a name match, and says so. A **seed** is a parameter of a composable under `ui/tv` whose
 * name starts with `initial` or `initially` and contains `Focus` (`initiallyFocused`,
 * `initialFocusedLabel`, `initialListHasFocus`). It is **covered** when a `screenshotTest` source
 * passes it a value other than its own name, or when it is forwarded from a parameter that is
 * covered (`initialColumnHasFocus = initialListHasFocus`). The honest limit: this reads names, not
 * the call graph, so it cannot tell that a frame reaches the one component that declares a name
 * shared by several, and a name any frame seeds covers every declaration of it. A frame that
 * seeds the wrong one passes here and is for review. A name nobody seeds anywhere fails, which is
 * the case that otherwise goes unnoticed.
 *
 * Fix a failure by adding a frame, in `app/src/screenshotTest`, that passes the seed, then
 * recording it with `./gradlew updateDebugScreenshotTest`. Do not widen the matching to make it
 * pass.
 */
class TvFocusSeedFramesTest {
    @Test
    fun `every focus seed under ui tv is passed by a screenshot frame`() {
        val main = sources("src/main/kotlin/io/github/scottcooper92/binge/seerr/ui/tv")
        val frames = sources("src/screenshotTest/kotlin")
        assertTrue("found no TV sources; is the working directory the module?", main.isNotEmpty() && frames.isNotEmpty())

        assertEquals(
            "TV focus seeds no frame passes — add a frame that seeds each",
            emptySet<String>(),
            uncoveredSeeds(main, frames),
        )
    }

    @Test
    fun `a seed no frame passes is reported`() {
        val main = listOf("fun Row(initiallyFocused: Boolean = false, initialFocusedLabel: String? = null) {}")
        val frames = listOf("Row(initiallyFocused = true)")

        assertEquals(setOf("initialFocusedLabel"), uncoveredSeeds(main, frames))
    }

    @Test
    fun `a frame that only forwards the seed's own name does not cover it`() {
        val main = listOf("fun Row(initiallyFocused: Boolean = false) {}")
        val frames = listOf("Row(initiallyFocused = initiallyFocused)")

        assertEquals(setOf("initiallyFocused"), uncoveredSeeds(main, frames))
    }

    @Test
    fun `a seed forwarded from a covered seed is covered`() {
        val main =
            listOf(
                "fun Pane(initialListHasFocus: Boolean = false) { Column(initialColumnHasFocus = initialListHasFocus) }",
                "fun Column(initialColumnHasFocus: Boolean = false) {}",
            )
        val frames = listOf("Pane(initialListHasFocus = true)")

        assertEquals(emptySet<String>(), uncoveredSeeds(main, frames))
    }

    private fun uncoveredSeeds(
        main: List<String>,
        frames: List<String>,
    ): Set<String> {
        val seeds = main.flatMap { declaration.findAll(it).map { m -> m.groupValues[1] } }.toSet()
        val covered =
            seeds
                .filter { seed -> frames.any { passesValueOtherThanItself(it, seed) } }
                .toMutableSet()
        do {
            val grew =
                seeds.filter { it !in covered }.any { seed ->
                    val forwardedFrom = main.flatMap { forwards(it, seed) }
                    forwardedFrom.any { it in covered }.also { if (it) covered += seed }
                }
        } while (grew)
        return seeds - covered
    }

    private fun passesValueOtherThanItself(
        source: String,
        seed: String,
    ): Boolean = Regex("""\b$seed\s*=\s*+(?!$seed\b)""").containsMatchIn(source)

    /** The seeds `source` hands to [seed] as `seed = other…`. */
    private fun forwards(
        source: String,
        seed: String,
    ): List<String> =
        Regex("""\b$seed\s*=\s*(\w+)""")
            .findAll(source)
            .map { it.groupValues[1] }
            .filter { it != seed && SEED_NAME.matches(it) }
            .toList()

    private fun sources(path: String): List<String> =
        File(path)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.readText() }
            .toList()

    private companion object {
        val SEED_NAME = Regex("""initial(ly)?\w*Focus\w*""")
        val declaration = Regex("""\b(initial(?:ly)?\w*Focus\w*)\s*:""")
    }
}
