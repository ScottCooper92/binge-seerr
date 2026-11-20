plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
}

// What the tests of every module share, so a per-module test does not carry its own copy: the fake server and the fake stores, the two
// ciphers, the profile routing, the scripted Seerr, and the rules and helpers for coroutines. Test code, so it is not in any module's
// coverage; it is held to the same lint, ktlint and detekt as the rest.
android {
    namespace = "com.cooper.binge.companion.seerr.testing"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
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

// Its public signatures name these types (a store's interface, a cipher, a rule), so a module that takes it sees them with it.
dependencies {
    api(project(":data"))
    api(libs.binge.companion.kit)
    api(libs.junit)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.coroutines.test)
    api(libs.okhttp)
    api(libs.okhttp.mockwebserver)
    api(libs.paging.runtime)
    api(libs.room.runtime)
    api(libs.androidx.test.ext.junit)
    implementation(libs.datastore.preferences)
    implementation(libs.homelab.appservices)
    implementation(libs.homelab.server)
    implementation(libs.homelab.bridge)
}

// The coroutines test API is experimental and the helpers here use it; opting the compilations in once keeps the log clean.
kotlin.target.compilations.configureEach {
    compileTaskProvider.configure { compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi") }
}

// No detekt gate here, on purpose. detekt scans production `src/main` only, and everything in this module is test support: it was in
// `data/src/testFixtures` and `app/src/test`, which detekt never scanned, and it is not production code now that it has a module of its
// own. The helpers are not at fault: `OkHttpDrain` needs the real `Dispatchers.Default`, and a fake server is meant to be large. ktlint,
// Android lint and `checkModuleRules` still run on it.
