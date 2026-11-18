import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Runs the task against real resolved graphs. The libraries come from a Maven repository written into a temporary folder, as POMs
 * with no jars, and the modules are real projects of a throwaway build, so resolution needs no network and the graph walk is the one
 * the build runs.
 */
class CheckModuleRulesTaskTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var repo: File

    private val rules =
        listOf(
            ModuleRule(path = ":app", projects = setOf(":data"), reason = "the application"),
            ModuleRule(
                path = ":data",
                projects = emptySet(),
                forbidden =
                    listOf(
                        "androidx.compose*",
                        "com.google.dagger*",
                        "com.binge:designsystem*",
                        "com.android.billingclient*",
                        "com.google.android.play*",
                        "io.exact:thing",
                    ),
                reason = "no UI, no graph",
            ),
        )

    @Before
    fun setUp() {
        repo = folder.newFolder("repo")
    }

    @Test
    fun `passes when a module keeps to its list and its libraries`() {
        pom("com.squareup.okhttp3", "okhttp", "5.0.0")
        val build = build()

        build.data.classpath("com.squareup.okhttp3:okhttp:5.0.0")
        build.app.classpath(project = build.data)

        build.data.check()
        build.app.check()
    }

    @Test
    fun `fails a module that depends on a module not on its list`() {
        val build = build()
        build.data.classpath(project = build.app)

        val failure = assertThrows(GradleException::class.java) { build.data.check() }

        assertEquals(listOf("runtime depends on the module :app, which is not on its list"), listed(failure))
    }

    @Test
    fun `fails a forbidden library, and names the reason once`() {
        pom("androidx.compose.ui", "ui", "1.0.0")
        val build = build()
        build.data.classpath("androidx.compose.ui:ui:1.0.0")

        val failure = assertThrows(GradleException::class.java) { build.data.check() }

        assertEquals(listOf("runtime has androidx.compose.ui:ui, which is forbidden"), listed(failure))
        assertEquals(1, Regex("no UI, no graph").findAll(failure.message.orEmpty()).count())
    }

    @Test
    fun `fails a forbidden library reached only through another library`() {
        pom("com.example", "wrapper", "1.0.0", "com.google.dagger:dagger:2.0.0")
        pom("com.google.dagger", "dagger", "2.0.0")
        val build = build()
        build.data.classpath("com.example:wrapper:1.0.0")

        val failure = assertThrows(GradleException::class.java) { build.data.check() }

        assertEquals(listOf("runtime has com.google.dagger:dagger, which is forbidden"), listed(failure))
    }

    @Test
    fun `matches an exact coordinate exactly and a prefix as a prefix`() {
        val data = rules.first { it.path == ":data" }

        assertTrue(data.forbids("io.exact:thing"))
        assertTrue(!data.forbids("io.exact:thing-else"))
        assertTrue(data.forbids("com.binge:designsystem-tv"))
        assertTrue(data.forbids("androidx.compose.runtime:runtime"))
        assertTrue(!data.forbids("androidx.room:room-runtime"))
        assertTrue(data.forbids("com.android.billingclient:billing-ktx"))
        assertTrue(data.forbids("com.google.android.play:review"))
    }

    @Test
    fun `fails a module with no row in the map`() {
        val build = build(withRules = rules.filter { it.path != ":data" })

        val failure = assertThrows(GradleException::class.java) { build.data.check() }

        assertTrue(failure.message.orEmpty().contains(":data has no row in the module map"))
    }

    private class Build(
        val rootProject: Project,
        val data: Module,
        val app: Module,
    )

    private inner class Module(
        val project: Project,
        private val rules: List<ModuleRule>,
    ) {
        private val names = linkedSetOf<String>()

        fun classpath(
            vararg coordinates: String,
            project: Module? = null,
            name: String = "runtime",
        ) {
            names += name
            val configuration = this.project.configurations.maybeCreate(name)
            coordinates.forEach { this.project.dependencies.add(name, it) }
            project?.let { this.project.dependencies.add(configuration.name, this.project.dependencies.project(mapOf("path" to it.project.path))) }
        }

        fun check(classpaths: List<String> = listOf("runtime")) {
            val task =
                project.tasks.register("checkModuleRules", CheckModuleRulesTask::class.java) {
                    module.set(project.path)
                    this.rules = this@Module.rules
                    classpaths.filter { it in names }.forEach { name ->
                        graphs.add(project.configurations.getByName(name).incoming.resolutionResult.rootComponent.map { ModuleGraph(project.path, name, it) })
                    }
                }.get()
            task.check()
        }
    }

    private fun build(withRules: List<ModuleRule> = rules): Build {
        val root = ProjectBuilder.builder().withProjectDir(folder.newFolder()).build()
        val data = child(root, "data", withRules)
        val app = child(root, "app", withRules)
        return Build(root, data, app)
    }

    private fun child(
        root: Project,
        name: String,
        rules: List<ModuleRule>,
    ): Module {
        val project = ProjectBuilder.builder().withName(name).withParent(root).withProjectDir(folder.newFolder()).build()
        project.pluginManager.apply("java-library")
        project.repositories.maven { url = repo.toURI() }
        return Module(project, rules)
    }

    private fun listed(failure: GradleException): List<String> = failure.message.orEmpty().lines().filter { it.startsWith("  - ") }.map { it.removePrefix("  - ") }

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
