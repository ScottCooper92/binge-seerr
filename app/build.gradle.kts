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
    alias(libs.plugins.screenshot)
}

// The exported schema is committed: a change to a table is a migration decision made in review.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "io.github.scottcooper92.binge.seerr"
    compileSdk = 37

    // What makes AGP create the screenshotTest source set. The matching flag is in gradle.properties;
    // both have to be present or the frames compile nowhere and the task does not exist.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

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
    implementation(libs.binge.companion.sdk)
    implementation(libs.binge.companion.contracts)
    implementation(libs.binge.designsystem)
    // The television surface in ui/tv: tv-material and the phone's Material 3 are never mixed in one
    // file, so that package imports only this module's theme and units.
    implementation(libs.binge.designsystem.tv)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.material3.adaptive.navigation3)
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.credentials)
    implementation(libs.play.services.blockstore)
    implementation(libs.kotlinx.coroutines.play.services)
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

    // The frames render against the same Compose the app ships, and the validation API is what
    // @PreviewTest resolves to.
    screenshotTestImplementation(platform(libs.compose.bom))
    screenshotTestImplementation(libs.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)

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

/**
 * The exception on a failed test, into the console and so into CI's own log. Gradle's default
 * renderer prints the exception's class and location and not its message, which is the hole
 * binge-ci's author-ci-fix job works around by downloading the reports artifact and extracting the
 * message out of Gradle's HTML.
 */
tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    // The screenshot render and validate tasks are Test tasks too, and they discover no JUnit tests:
    // the plugin renders @Preview functions rather than executing test code. The plugin also warns
    // unless it has the fork to itself.
    if (name.contains("ScreenshotTest")) {
        failOnNoDiscoveredTests = false
        maxParallelForks = 1
    } else {
        // Where CoroutineLeakReporter writes. Under build/reports/tests so ci.yml's artifact already
        // carries it: a leak reports between tests, which is exactly where Gradle's per-test capture
        // has nowhere to put it (#177).
        val leakLog =
            layout.buildDirectory
                .file("reports/tests/$name/coroutine-leaks.txt")
                .get()
                .asFile
        systemProperty("binge.coroutineLeakLog", leakLog.absolutePath)
        // Gradle does not clear this directory between runs, and a leak file is only written when
        // there is a leak — so without this a clean run still shows the last dirty one's.
        doFirst { leakLog.delete() }
    }
}

// The screenshot plugin does not wire itself into `check` the way ktlint and AGP's lint do, so
// without this `./gradlew build` — which is this repository's whole gate, and all CI runs — would
// carry the frames and never compare them. Named here rather than in ci.yml so a local build and a
// CI build answer the same question (#146).
tasks.named("check") {
    dependsOn("validateDebugScreenshotTest")
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

/**
 * detekt with the baseline switched off, so [checkBaselineStaleness] can see which findings the code
 * still produces. Findings are expected, so it never fails on its own.
 */
val detektWithoutBaseline by tasks.registering(io.gitlab.arturbosch.detekt.Detekt::class) {
    description = "Runs detekt with no baseline applied, for checkBaselineStaleness."
    ignoreFailures = true
    baseline.set(null as org.gradle.api.file.RegularFile?)
    // The extension's config reaches only the tasks the plugin creates. Without these two a
    // hand-registered task runs detekt's defaults, where half this repository's rules are inactive
    // and every baselined finding of one reads as fixed.
    config.setFrom(layout.settingsDirectory.file("detekt.yml"))
    buildUponDefaultConfig = true
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/detekt/no-baseline.xml"))
    }
}

/**
 * Fails when `detekt-baseline.xml` holds an entry the code no longer produces.
 *
 * An orphaned entry is worse than clutter: it reads as debt still owed, and it silently absorbs the
 * next real finding of that rule in that file. Nothing else can see one — detekt passes on an entry
 * that matches nothing — which is how a bad merge resolution reverted a paydown and stayed green
 * (#283). Entries are compared per rule and file rather than by their full signature, because the
 * report carries no signature; a rule with several findings in one file is therefore counted.
 */
val checkBaselineStaleness by tasks.registering {
    description = "Fails when detekt-baseline.xml holds an entry the code no longer produces."
    group = "verification"
    val baselineFile = layout.settingsDirectory.file("detekt-baseline.xml").asFile
    val reportFile =
        layout.buildDirectory
            .file("reports/detekt/no-baseline.xml")
            .get()
            .asFile
    dependsOn(detektWithoutBaseline)
    inputs.file(baselineFile)
    inputs.file(reportFile)
    outputs.upToDateWhen { true }
    doLast {
        val baselined = mutableMapOf<String, Int>()
        Regex("<ID>([^<]+)</ID>").findAll(baselineFile.readText()).forEach { match ->
            val id = match.groupValues[1]
            val rule = id.substringBefore(':')
            val file = id.substringAfter(':').substringBefore('$')
            baselined.merge("$rule in $file", 1, Int::plus)
        }
        val reported = mutableMapOf<String, Int>()
        var currentFile = ""
        reportFile.readLines().forEach { line ->
            Regex("""<file name="([^"]+)"""").find(line)?.let { currentFile = it.groupValues[1].substringAfterLast('/') }
            Regex("""source="detekt\.([^"]+)"""").find(line)?.let {
                reported.merge("${it.groupValues[1]} in $currentFile", 1, Int::plus)
            }
        }
        val stale =
            baselined
                .filter { (key, count) -> count > (reported[key] ?: 0) }
                .map { (key, count) -> "  $key — baselined $count, reported ${reported[key] ?: 0}" }
        if (stale.isNotEmpty()) {
            error(
                "detekt-baseline.xml holds ${stale.size} entr${if (stale.size == 1) "y" else "ies"} " +
                    "the code no longer produces:\n" + stale.sorted().joinToString("\n") +
                    "\n\nDelete them. An entry that matches nothing absorbs the next real finding of " +
                    "that rule in that file.",
            )
        }
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

// `build` depends on `check`, so the staleness gate runs where every other one does rather than
// needing a line of its own in CI.
tasks.named("check") { dependsOn(checkBaselineStaleness) }
