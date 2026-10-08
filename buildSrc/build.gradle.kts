// The build-logic types this repository carries: the translation-staleness task, ported from Binge's
// convention plugin, and the gRPC alignment check. Everything else configures the app module directly.
plugins {
    `kotlin-dsl`
}

// Google's mirror of Central first, as in the root settings: buildSrc resolves its own dependencies, including the
// Kotlin that kotlin-dsl brings, and a rate-limited Central would otherwise fail it before the app configures.
repositories {
    maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
    mavenCentral()
}

// A gate that breaks usually breaks by passing everything, so each task is tested against a case it
// must pass and a case it must fail. ProjectBuilder comes with the Gradle API that kotlin-dsl adds.
dependencies {
    testImplementation(libs.junit)
}

// Gradle builds only buildSrc's jar before the app configures, never its `check`, and the root build
// cannot depend on a buildSrc task. So the tests finalize the jar, which the test compile itself needs:
// a gate that stopped failing breaks every build before any app task runs. They are up to date unless
// buildSrc changed.
tasks.named("jar") { finalizedBy(tasks.named("test")) }
