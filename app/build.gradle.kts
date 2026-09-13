import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
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
        // release.yml overrides both from the tag it builds; a local build keeps the placeholders.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
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

    // Generates the locale config from the values-<lang> directories present, so Android 13+ offers
    // this app in the per-app language picker and adding a locale is one directory.
    androidResources { generateLocaleConfig = true }

    // A locale is complete or it does not exist: the alternative is not English, it is Android's
    // per-string fallback rendering half a screen in each. Adding an English string means adding
    // its translation in the same change.
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

    // The release keystore is never in the repository: the values come from keystore.properties at
    // the root (gitignored) for a local signed build, or from the environment in release.yml.
    // Without them the release build type gets no signing config at all, so it still builds
    // everywhere, but the output is an unsigned APK/bundle rather than a debug-signed one.
    val keystoreProperties =
        Properties().apply {
            val file = rootDir.resolve("keystore.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }

    fun signingValue(key: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(key)
    val releaseStoreFile = signingValue("RELEASE_STORE_FILE")

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootDir.resolve(releaseStoreFile)
                storePassword = signingValue("RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("RELEASE_KEY_ALIAS")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // en-XA lengthens and accents every string, ar-XB mirrors the layout: truncation and RTL
            // bugs found with no translation written. Debug only; they must never ship.
            isPseudoLocalesEnabled = true
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
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// A reworded English string is the one translation drift lint cannot see, so the hash of every
// translated source string is committed at the repo root and `check` fails when one moves. Fix or
// re-read the translation it names, then re-stamp with `updateTranslationHashes`.
val translatedStringFiles =
    fileTree(projectDir) {
        include("src/*/res/values/strings.xml", "src/*/res/values-*/strings.xml")
    }
val translationHashes = rootProject.layout.projectDirectory.file("translation-hashes.txt")

tasks.register<CheckTranslationStalenessTask>("checkTranslationStaleness") {
    group = "verification"
    description = "Checks that no translated source string changed without its translations being re-confirmed."
    stringFiles.from(translatedStringFiles)
    hashFile.set(translationHashes)
    repoRoot.set(rootProject.layout.projectDirectory)
    rewrite.set(false)
}

tasks.register<CheckTranslationStalenessTask>("updateTranslationHashes") {
    group = "verification"
    description = "Re-stamps translation-hashes.txt after the named translations have been re-read."
    stringFiles.from(translatedStringFiles)
    hashFile.set(translationHashes)
    repoRoot.set(rootProject.layout.projectDirectory)
    rewrite.set(true)
}

tasks.named("check") { dependsOn("checkTranslationStaleness") }

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

// detekt is pinned to production `src/main`: a test's shape is not the app's, and Binge pins the
// same way. *PreviewData.kt is preview tooling rather than logic, so it is dropped too.
detekt {
    config.setFrom(layout.settingsDirectory.file("detekt.yml"))
    baseline = layout.settingsDirectory.file("detekt-baseline.xml").asFile
    buildUponDefaultConfig = true
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    setSource(
        fileTree("src/main") {
            include("**/*.kt")
            exclude("**/*PreviewData.kt")
        },
    )
    jvmTarget = "17"
    reports {
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    setSource(
        fileTree("src/main") {
            include("**/*.kt")
            exclude("**/*PreviewData.kt")
        },
    )
    jvmTarget = "17"
}

// UnsafeCallOnNullableType - the `!!` ban - needs type resolution, and a detekt task carries no
// classpath by default, so without this the rule loads and silently never fires. `libraries` is the
// compile task's already-variant-resolved classpath; resolving compileDependencyFiles directly trips
// AGP 9 variant ambiguity. configureEach rather than a lookup, because the Android variant
// compilations do not exist yet when the Kotlin plugin applies.
kotlin.target.compilations.configureEach {
    if (name != "debug") return@configureEach
    val classpathFiles =
        compileTaskProvider.map {
            (it as org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool).libraries
        }
    val outputClasses = output.classesDirs
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        classpath.from(classpathFiles, outputClasses)
    }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        classpath.from(classpathFiles, outputClasses)
    }
}

// The gate measures logic: ViewModels, the Seerr client and its error mapping, the stores, the
// mappers and the exported service. Everything excluded below is either generated or an Android
// entry point with nothing a JVM unit test can reach - never a class that simply lacks tests.
kover {
    reports {
        filters {
            excludes {
                classes(
                    // Hilt's generated graph. The patterns match fully-qualified names, so each
                    // needs a leading `*`, and this app's providers generate `<Module>_<Name>Factory`
                    // rather than a bare `_Factory` - hence the whole `di` package rather than a
                    // name pattern that would silently match none of them. `SeerrModule` itself is
                    // hand-written, but it is wiring: every function returns a constructed object.
                    "*_HiltModules*",
                    "*_MembersInjector*",
                    "*Hilt_*",
                    "hilt_aggregated_deps.*",
                    "io.github.scottcooper92.binge.seerr.di.*",
                    // Room's generated DAO and database implementations, including the inner
                    // classes it emits for paging sources and open delegates - which is why this
                    // is `*_Impl*` and not `*_Impl`.
                    "*_Impl*",
                    "*ComposableSingletons*",
                    "*PreviewData*",
                    // The Android entry points: an Activity's onCreate and the Application class.
                    // Their content is `setContent { … }` and Hilt's own initialisation.
                    "io.github.scottcooper92.binge.seerr.MainActivity*",
                    "io.github.scottcooper92.binge.seerr.AdvancedRequestActivity*",
                    "io.github.scottcooper92.binge.seerr.SeerrApp",
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
        // 78, measured: the suite sits at 80.3% today. Chosen rather than inherited - Binge's 70
        // is derived from its own per-module layout, and this is one module holding ViewModels,
        // the client, the stores and the service together. The ~2 points of headroom are about
        // 180 lines, so a feature landing without tests trips this and a class or two that is
        // genuinely awkward to reach does not. Ratchet it up as ui/state and ui/tv are covered.
        verify {
            rule {
                minBound(78)
            }
        }
    }
}
