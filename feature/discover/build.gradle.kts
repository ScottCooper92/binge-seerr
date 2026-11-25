plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
    id("binge.gates.baseline-staleness")
}

// Discovery on the phone: the Movies and TV hubs, the grids, title search, and the title, series, person and collection pages with the
// request sheet. It knows the Seerr client (:data), the shared screen pieces (:core:ui) and the design system, and no other feature.
// The nav host, every route's entry and the pieces that reach another feature (the request-detail sheets) stay in :app, which hands
// them in through composition locals. The frames that render these screens stay in :app's screenshotTest source set, which compiles
// against :app's classpath, and that includes this module.
android {
    namespace = "com.cooper.binge.companion.seerr.feature.discover"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    // The translation checks are errors here too, for the same reason as in :app: a locale is complete or it does not exist.
    lint {
        error +=
            setOf(
                "MissingTranslation",
                "ExtraTranslation",
                "MissingQuantity",
                "UnusedQuantity",
                "StringFormatMatches",
                "StringFormatCount",
            )
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":core:ui"))
    implementation(libs.binge.companion.sdk)
    implementation(libs.binge.designsystem)
    implementation(libs.homelab.appservices)
    implementation(libs.homelab.presentation)
    implementation(libs.homelab.uiandroid)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.navigation3.runtime)
    implementation(libs.okhttp)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
}

detektGate {
    baselineFile = layout.projectDirectory.file("detekt-baseline.xml")
    customRulesProject = ":detekt-rules"
    // detekt 2.0.0-alpha.6 crashed twice with an array index error in its per-file futures (#2179); one file at a time.
    parallel = false
}
