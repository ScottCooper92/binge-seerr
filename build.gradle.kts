// Root build file — plugin declarations only, matching Binge's layout. Configuration lives
// in the module that needs it; there is only one module until the extraction adds more.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ktlint) apply false
}
