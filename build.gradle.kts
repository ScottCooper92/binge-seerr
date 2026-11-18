// Root build file — plugin declarations only, matching Binge's layout. Configuration lives
// in the module that needs it: :app, and :detekt-rules, which holds the binge: detekt rules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
    // Declared and never applied: this is what puts Kotlin 2.4.21 on the build classpath for AGP's
    // built-in Kotlin support to use. See the note on `kotlin` in libs.versions.toml.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    // Applied by :app only when a google-services.json is present; see the note there.
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// The module map (buildSrc/src/main/kotlin/ModuleRules.kt): what each module may depend on, checked against the resolved
// classpaths so a library that arrives through another one is caught too. Every module gets the check, so a module with no row in
// the map fails the build, and it runs in that module's `check`, which `./gradlew build` carries.
subprojects {
    val module = this
    module.afterEvaluate {
        val check =
            module.tasks.register<CheckModuleRulesTask>("checkModuleRules") {
                group = "verification"
                description = "Checks this module's dependencies against the module map."
                this.module.set(module.path)
                // An Android module's classpaths are the debug variant's; a plain JVM module's have no variant in the name. This list is
            // the scope of the check: test and release classpaths are not looked at, so test libraries are free and a
            // `releaseImplementation` would pass.
                listOf("debugCompileClasspath", "debugRuntimeClasspath", "compileClasspath", "runtimeClasspath").forEach { name ->
                    module.configurations.findByName(name)?.let { configuration ->
                        graphs.add(configuration.incoming.resolutionResult.rootComponent.map { ModuleGraph(module.path, name, it) })
                    }
                }
            }
        module.tasks.findByName("check")?.dependsOn(check)
    }
}
