pluginManagement {
    // The shared Gradle gates come from binge-ci as source, through a submodule pinned to the release
    // the workflows also pin (#1082). Its plugins are applied by id with no version.
    includeBuild("binge-ci/gradle-plugins")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Google's mirror of Maven Central, asked first. Shared build hosts (CI runners, cloud agent
        // sessions) are rate-limited by Central itself and get 429s mid-build; the mirror serves the
        // same artifacts from Google's CDN. Anything it lacks falls through to mavenCentral() below.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        // The portal before Central: some plugins (ktlint's, for one) are published only there, and a 429 from
        // Central would fail the build before the portal was asked.
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // The same mirror, ahead of Central, for the same reason as above.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        mavenCentral()
    }

    // Catalog at the root rather than gradle/, matching Binge, so a version bump can be
    // applied to both repositories the same way.
    versionCatalogs {
        create("libs") { from(files("libs.versions.toml")) }
    }
}

rootProject.name = "binge-seerr"

// The contracts and the SDK come from binge-companions as SOURCE, through a git submodule and a
// composite build, rather than as a published artifact: nothing is published yet, and a companion
// author reading this repository gets the exact contract this app was built against. Gradle
// substitutes `io.github.scottcooper92:binge-companion-contracts` and `:binge-companion-sdk` with the included
// build's projects.
includeBuild("binge-companions")

// The shared design system arrives the same way (Binge#2427): the theme and the components this
// app's screen wears come from binge-design-system as source, so a user moving between Binge and
// this app sees one product without this app depending on Binge. Gradle substitutes
// `com.binge:designsystem` with the included build's project, and `com.binge:designsystem-tv` — the
// tv-material foundation the television surface is built from — with its TV module.
includeBuild("design-system") {
    dependencySubstitution {
        substitute(module("com.binge:designsystem")).using(project(":designsystem"))
        substitute(module("com.binge:designsystem-tv")).using(project(":designsystem-tv"))
    }
}

// The shared app library, as source the same way (a private submodule: the licence exception covers linking it). Gradle
// substitutes `com.cooper.homelab:app-services` with the included build's project.
includeBuild("homelab-core") {
    dependencySubstitution {
        substitute(module("com.cooper.homelab:app-services")).using(project(":app-services"))
        substitute(module("com.cooper.homelab:app-services-play")).using(project(":app-services-play"))
        substitute(module("com.cooper.homelab:app-services-posthog")).using(project(":app-services-posthog"))
        substitute(module("com.cooper.homelab:app-services-firebase")).using(project(":app-services-firebase"))
        substitute(module("com.cooper.homelab:server")).using(project(":server"))
        substitute(module("com.cooper.homelab:presentation")).using(project(":presentation"))
        substitute(module("com.cooper.homelab:ui-android")).using(project(":ui-android"))
        substitute(module("com.cooper.homelab:testing")).using(project(":testing"))
    }
}

include(":app")
include(":data")
// What the tests of every module share: the fakes, the fake server, the ciphers and the coroutine helpers.
include(":core:testing")
// The binge: detekt comment rules, copied from binge-companions per binge-companions#39 and loaded by :app's detekt run.
include(":detekt-rules")
