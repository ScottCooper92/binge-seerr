import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Fails the build when an `io.grpc` module resolves to a version other than the root catalog's `grpc`.
 *
 * The app does not declare `grpc-binder`: it arrives through the binge-companions SDK at that build's
 * catalog version. So bumping `grpc` here alone moves nothing in the APK, and only the test-only
 * `grpc-inprocess` moves, which pulls `grpc-core` past the binder it has to match. Both builds stay
 * green. Checking the resolved graphs, not the two catalog files, catches that drift in either
 * direction, and anything else that drags one gRPC module away from the rest.
 *
 * grpc-kotlin is versioned separately from grpc, so its modules are not held to this.
 */
@DisableCachingByDefault(because = "Inspects resolved dependency graphs; cheap, and not worth caching.")
abstract class CheckGrpcAlignmentTask : DefaultTask() {
    @get:Input
    abstract val expectedVersion: Property<String>

    /** The root of each runtime classpath to check, from `incoming.resolutionResult.rootComponent`. */
    @get:Input
    abstract val graphs: ListProperty<ResolvedComponentResult>

    @TaskAction
    fun check() {
        val expected = expectedVersion.get()
        val misaligned =
            graphs
                .get()
                .flatMap { root -> root.grpcModules().map { "${it.module} ${it.version} (in ${root.variants.firstOrNull()?.displayName ?: "?"})" } }
                .filterNot { it.split(' ')[1] == expected }
                .distinct()
                .sorted()
        if (misaligned.isNotEmpty()) {
            throw GradleException(
                "Every io.grpc module must resolve to the catalog's grpc = $expected, but:\n" +
                    misaligned.joinToString("\n") { "  - $it" } +
                    "\nThe SDK brings grpc-binder at binge-companions' catalog version: bump the " +
                    "binge-companions submodule to a revision on the same grpc, in a commit of its own.",
            )
        }
    }

    private fun ResolvedComponentResult.grpcModules(): List<ModuleComponentIdentifier> {
        val seen = mutableSetOf<ResolvedComponentResult>()
        val queue = ArrayDeque(listOf(this))
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component)) continue
            component.dependencies
                .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                .forEach { queue.add(it.selected) }
        }
        return seen
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .filter { it.group == "io.grpc" && !it.module.startsWith("grpc-kotlin") }
    }
}
