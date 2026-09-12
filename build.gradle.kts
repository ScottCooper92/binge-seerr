// Root build file — plugin declarations only, matching Binge's layout. Configuration lives
// in the module that needs it; there is only one module until the extraction adds more.
plugins {
    alias(libs.plugins.android.application) apply false
    // Declared and never applied: this is what puts Kotlin 2.4.10 on the build classpath for AGP's
    // built-in Kotlin support to use. See the note on `kotlin` in libs.versions.toml.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
