plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
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
    }

    // BuildConfig.DEBUG selects the caller policy: any caller on a debug build, the pinned Binge
    // certificate on release. A flag a user could flip must never make that choice.
    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            // No shrinking yet. The APK cost after R8 is something roadmap stage 3 measures,
            // so turning it on before there is anything to shrink would report a number that
            // means nothing.
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.binge.integration.sdk)
    implementation(libs.binge.integration.contracts)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The generated service is driven over an in-process channel: the whole contract, no device.
    testImplementation(libs.grpc.inprocess)
}
