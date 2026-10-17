plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

// The binge: comment rules, as a plain JVM jar that :app's detekt run loads as binge.gates.detekt's
// `customRulesProject` (app/build.gradle.kts). A copy of binge-companions' detekt-rules module, per
// binge-companions#39: nothing is published, so each repository that wants the rules carries them, and
// this gate does not depend on another repository's build. It must not apply detekt itself, since
// detekt cannot analyse the ruleset that defines it, and it has no coverage floor: there is no app logic
// here for one to mean anything against. The rules are covered by tests through dev.detekt:detekt-test.
dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test>().configureEach {
    // BingeRuleSetProviderTest reads the real detekt.yml, so a change to it re-runs the tests rather
    // than being served from an up-to-date check or the build cache.
    inputs.file(rootProject.file("detekt.yml")).withPropertyName("detektConfig")
}
