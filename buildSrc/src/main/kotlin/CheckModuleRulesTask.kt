import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** The resolved graph of one classpath of one module, such as `:data`'s `debugRuntimeClasspath`. */
class ModuleGraph(
    val module: String,
    val classpath: String,
    val root: ResolvedComponentResult,
)

/**
 * Fails the build when this module depends on what [MODULE_RULES] does not let it, or when it has no row there. The build registers
 * one for every module, so a new module is checked without anyone opting it in.
 *
 * It reads the resolved graphs, not the build files: a library that arrives through another library is as much in the module
 * as one it declares, and a build file cannot show that. It looks at the main classpaths only, so test libraries are free.
 *
 * Only projects of this build count as modules. A project of an included build (the shared libraries and the design system arrive
 * that way) is judged by its coordinates, like any other library.
 */
@DisableCachingByDefault(because = "Inspects resolved dependency graphs; cheap, and not worth caching.")
abstract class CheckModuleRulesTask : DefaultTask() {
    /** The Gradle path of the module this task belongs to. */
    @get:Internal
    abstract val module: Property<String>

    /** The classpaths of that module to check. */
    @get:Internal
    abstract val graphs: ListProperty<ModuleGraph>

    /** The rules in force. A test replaces them; the build uses the table. */
    @get:Internal
    var rules: List<ModuleRule> = MODULE_RULES

    @TaskAction
    fun check() {
        val problems = listOfNotNull(unlisted()) + graphs.get().flatMap { it.violations() }
        if (problems.isNotEmpty()) {
            val reason = rules.firstOrNull { it.path == module.get() }?.reason
            throw GradleException(
                "The module rules are broken in ${module.get()}:\n" + problems.distinct().sorted().joinToString("\n") { "  - $it" } +
                    (reason?.let { "\n${module.get()} is held to this: $it" } ?: "") +
                    "\nFix the dependency. The rules are the table in buildSrc/src/main/kotlin/ModuleRules.kt, which is changed " +
                    "when the design of the modules changes (#2163) and not to make a build pass.",
            )
        }
    }

    private fun unlisted(): String? =
        module.get().takeIf { path -> rules.none { it.path == path } }
            ?.let { "$it has no row in the module map. Add one to ModuleRules.kt, with what it may depend on and why." }

    private fun ModuleGraph.violations(): List<String> {
        val rule = rules.firstOrNull { it.path == module } ?: return emptyList()
        return components().flatMap { component ->
            val project = (component.id as? ProjectComponentIdentifier)?.takeIf { it.build.buildPath == ":" }?.projectPath
            val coordinate = component.moduleVersion?.let { "${it.group}:${it.name}" }
            listOfNotNull(
                project?.takeIf { it != module && it !in rule.projects }
                    ?.let { "$classpath depends on the module $it, which is not on its list" },
                coordinate?.takeIf { project == null && rule.forbids(it) }
                    ?.let { "$classpath has $it, which is forbidden" },
            )
        }
    }

    private fun ModuleGraph.components(): Set<ResolvedComponentResult> {
        val seen = linkedSetOf<ResolvedComponentResult>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component)) continue
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue.add(it.selected) }
        }
        return seen
    }
}
