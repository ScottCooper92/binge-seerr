plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
    id("binge.gates.baseline-staleness")
}

// The exported schema is committed: a change to a table is a migration decision made in review.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.cooper.binge.companion.seerr.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    testOptions {
        // The Room and DataStore tests need a real Context, which Robolectric supplies.
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.binge.companion.sdk)
    implementation(libs.binge.companion.kit)
    implementation(libs.binge.companion.contracts)
    implementation(libs.homelab.appservices)
    implementation(libs.homelab.server)
    implementation(libs.homelab.bridge)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.datastore.preferences)
    implementation(libs.paging.runtime)
    implementation(libs.room.runtime)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.paging.testing)
    testImplementation(libs.robolectric)
    // ApplicationProvider, for the tests that need a real Context.
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.homelab.testing)
    // The fakes its tests share with :app's: a fake server, a fake DAO, the two ciphers, the profile routing and the coroutine helpers.
    testImplementation(project(":core:testing"))
}

tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    // Robolectric fetches its android-all jars itself, outside Gradle's repositories, so the mirror in settings.gradle.kts does
    // not reach it. Point it at the same mirror: Central answers bursts from shared runners with a 429.
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
    systemProperty("robolectric.dependency.repo.id", "MavenCentralMirror")
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
}

// The coroutines test API is experimental and nearly every test uses it; opting the test compilations in once keeps the log clean.
kotlin.target.compilations.configureEach {
    if (!name.contains("Test")) return@configureEach
    compileTaskProvider.configure { compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi") }
}

// binge-ci's detekt gate, as :app runs it, with this module's own baseline: its one entry is for a file that lives here, and the
// baseline-staleness check would call it stale from any other module. The binge: comment rules load from :detekt-rules.
detektGate {
    baselineFile = layout.projectDirectory.file("detekt-baseline.xml")
    customRulesProject = ":detekt-rules"
    // detekt 2.0.0-alpha.6 crashed twice with an array index error in its per-file futures (#2179); one file at a time.
    parallel = false
}

// The gate measures logic. Room's generated DAO and database implementations, including the inner classes it emits for paging
// sources and open delegates, are excluded: the patterns match fully-qualified names, so this is `*_Impl*` and not `*_Impl`.
kover {
    reports {
        filters {
            excludes {
                classes("*_Impl*")
            }
        }
        // 70, measured: this module's own tests cover 71.2% of its lines. :app's tests exercise the rest through the screens and
        // view models, and its floor is measured over both modules together, as it was when they were one. Ratchet this up
        // as the stores and the client gain tests of their own.
        verify {
            rule {
                minBound(70)
            }
        }
    }
}
