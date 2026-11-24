plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
    id("binge.gates.baseline-staleness")
    id("binge.gates.tv-material-separation")
}

// The pieces every screen shares: the loading skeletons, the error and message dialogs, the row and sheet scaffolds, and the theme
// that projects this app's brand into the design system's. They know the Seerr error types and the design system, and no feature, so
// a feature module can take them without taking another feature. The frames that render them stay in :app's screenshotTest source set:
// that source set compiles against :app's classpath, which includes this module.
android {
    namespace = "com.cooper.binge.companion.seerr.core.ui"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures {
        compose = true
        // ServiceApp reads BuildConfig.DEBUG, which a library has only when asked.
        buildConfig = true
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
    implementation(libs.binge.companion.sdk)
    implementation(libs.binge.companion.contracts)
    implementation(libs.binge.designsystem)
    // SeerrTheme projects the colour schemes into tv-material's.
    implementation(libs.binge.designsystem.tv)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    // The qualifier the view models of every feature inject their dispatcher by; it is only an annotation, so no Dagger.
    implementation(libs.javax.inject)
    implementation(libs.homelab.appservices)
    implementation(libs.homelab.server)
    implementation(libs.homelab.uiandroid)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.navigation3.runtime)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.retrofit)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
}

detektGate {
    baselineFile = layout.projectDirectory.file("detekt-baseline.xml")
    customRulesProject = ":detekt-rules"
    // detekt 2.0.0-alpha.6 crashed twice with an array index error in its per-file futures (#2179); one file at a time.
    parallel = false
}

// Material 3 and tv-material each ship a MaterialTheme under the same simple name; SeerrTheme.kt is the one file allowed to cross.
tvMaterialSeparation {
    sources.from(fileTree("src") { include("**/*.kt") })
    allowlist.add("core/ui/src/main/kotlin/com/cooper/binge/companion/seerr/theme/SeerrTheme.kt")
    themeTypeNames.addAll("SeerrDarkColorScheme", "SeerrLightColorScheme")
}
