import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Runs the task against real resolved graphs. The modules come from a Maven repository written into
 * a temporary folder, as POMs with no jars, so resolution needs no network and the graph walk is the
 * one the build runs.
 */
class CheckGrpcAlignmentTaskTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `passes when every io_grpc module is on the expected version`() {
        val task = taskResolving("io.grpc:grpc-binder:1.76.3")

        task.check()
    }

    @Test
    fun `fails and names a module that drifts, even one reached transitively`() {
        // grpc-binder 1.76.3 is aligned itself but brings grpc-api 1.75.0.
        pom("io.grpc", "grpc-binder", "1.76.3", "io.grpc:grpc-api:1.75.0")
        val task = taskResolving("io.grpc:grpc-binder:1.76.3")

        val failure = assertThrows(GradleException::class.java) { task.check() }

        assertEquals(listOf("grpc-api 1.75.0 (in runtime)"), listedModules(failure))
    }

    @Test
    fun `ignores grpc-kotlin, which is versioned on its own`() {
        val task = taskResolving("io.grpc:grpc-binder:1.76.3", "io.grpc:grpc-kotlin-stub:1.5.0")

        task.check()
    }

    @Test
    fun `checks every graph it is given`() {
        val project = project()
        val task =
            project.alignmentTask(
                project.graphOf("release", "io.grpc:grpc-binder:1.76.3"),
                project.graphOf("unitTest", "io.grpc:grpc-core:1.75.0"),
            )

        val failure = assertThrows(GradleException::class.java) { task.check() }

        assertEquals(listOf("grpc-api 1.75.0 (in unitTest)", "grpc-core 1.75.0 (in unitTest)"), listedModules(failure))
    }

    private lateinit var repo: File

    /** The modules a failure lists, one per `  - ` line. */
    private fun listedModules(failure: GradleException) =
        failure.message!!.lines().filter { it.startsWith("  - ") }.map { it.removePrefix("  - ") }

    @Before
    fun writeRepository() {
        repo = folder.newFolder("repo")
        pom("io.grpc", "grpc-api", "1.76.3")
        pom("io.grpc", "grpc-api", "1.75.0")
        pom("io.grpc", "grpc-core", "1.76.3", "io.grpc:grpc-api:1.76.3")
        pom("io.grpc", "grpc-core", "1.75.0", "io.grpc:grpc-api:1.75.0")
        pom("io.grpc", "grpc-binder", "1.76.3", "io.grpc:grpc-core:1.76.3")
        pom("io.grpc", "grpc-kotlin-stub", "1.5.0", "io.grpc:grpc-api:1.76.3")
    }

    private fun taskResolving(vararg coordinates: String): CheckGrpcAlignmentTask {
        val project = project()
        return project.alignmentTask(project.graphOf("runtime", *coordinates))
    }

    private fun project(): Project {
        val project = ProjectBuilder.builder().withProjectDir(folder.newFolder()).build()
        project.repositories.maven { url = repo.toURI() }
        return project
    }

    private fun Project.graphOf(
        name: String,
        vararg coordinates: String,
    ) = configurations.create(name).let { configuration ->
        coordinates.forEach { dependencies.add(name, it) }
        configuration.incoming.resolutionResult.rootComponent
    }

    private fun Project.alignmentTask(vararg graphs: Provider<ResolvedComponentResult>) =
        tasks.register("checkGrpcAlignment", CheckGrpcAlignmentTask::class.java) {
            expectedVersion.set("1.76.3")
            graphs.forEach(this.graphs::add)
        }.get()

    /** Writes a POM, replacing any earlier one at the same coordinates. */
    private fun pom(
        group: String,
        module: String,
        version: String,
        vararg dependencies: String,
    ) {
        val dir = File(repo, "${group.replace('.', '/')}/$module/$version").apply { mkdirs() }
        val dependencyXml =
            dependencies.joinToString("") { coordinate ->
                val (g, m, v) = coordinate.split(':')
                "<dependency><groupId>$g</groupId><artifactId>$m</artifactId><version>$v</version></dependency>"
            }
        File(dir, "$module-$version.pom").writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$module</artifactId>
              <version>$version</version>
              <packaging>pom</packaging>
              <dependencies>$dependencyXml</dependencies>
            </project>
            """.trimIndent(),
        )
    }
}
