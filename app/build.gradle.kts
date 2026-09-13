plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.ktlint)
}

// The exported schema is committed: a change to a table is a migration decision made in review.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "io.github.scottcooper92.binge.seerr"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.scottcooper92.binge.seerr"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // The device lane's tests take the object graph from Hilt, so the runner swaps in Hilt's test
        // Application; it is the only instrumentation this app runs.
        testInstrumentationRunner = "io.github.scottcooper92.binge.seerr.SeerrHiltTestRunner"
    }

    // BuildConfig.DEBUG selects the caller policy: any caller on a debug build, the pinned Binge
    // certificate on release. A flag a user could flip must never make that choice.
    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            // Opt-in (`-PminifyDebug`) minified debug build: the one the device lane runs, so a keep
            // rule that goes stale fails there rather than on a user. BuildConfig.DEBUG stays true, which
            // is what lets the smoke test bind as a caller. Off by default: it would slow the dev loop.
            if (project.hasProperty("minifyDebug")) {
                optimization { enable = true }
            }
        }
        release {
            // One switch for code shrinking, resource shrinking and the default Android keep rules;
            // this app's own rules are the `keepRules` source set. Must stay identical to the
            // minifyDebug block above, or the lane stops proving what release ships.
            optimization { enable = true }
        }
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

    testOptions {
        // The TV focus tests drive a real D-pad through Robolectric on the JVM, against screens built
        // out of dimensionResource and stringResource, so they need the merged resources on the
        // unit-test classpath.
        unitTests.isIncludeAndroidResources = true

        managedDevices {
            localDevices {
                // The device lane's emulator (#86), driven by device-smoke.yml and never by ci.yml.
                // aosp-atd is headless and carries no Google APIs, which the smoke does not need.
                create("smokeAtdApi34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.binge.integration.sdk)
    implementation(libs.binge.integration.contracts)
    implementation(libs.binge.designsystem)
    // The television surface in ui/tv: tv-material and the phone's Material 3 are never mixed in one
    // file, so that package imports only this module's theme and units.
    implementation(libs.binge.designsystem.tv)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.browser)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.paging.testing)
    // The generated service is driven over an in-process channel: the whole contract, no device.
    testImplementation(libs.grpc.inprocess)
    // The TV focus tests: Robolectric hosts Compose on the JVM, and the television qualifier makes
    // its 2D focus search behave like a panel's rather than a handset's.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // The device lane (#86): one instrumentation test that binds the exported Service over a real
    // Binder on the minified build and completes a handshake and a status call against a mock server.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
