pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
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
        mavenCentral()
    }

    // Catalog at the root rather than gradle/, matching Binge, so a version bump can be
    // applied to both repositories the same way.
    versionCatalogs {
        create("libs") { from(files("libs.versions.toml")) }
    }
}

rootProject.name = "binge-seerr"

// The contracts and the SDK come from binge-integrations as SOURCE, through a git submodule and a
// composite build, rather than as a published artifact: nothing is published yet, and a companion
// author reading this repository gets the exact contract this app was built against. Gradle
// substitutes `io.github.scottcooper92:contracts` and `:sdk` with the included build's projects.
includeBuild("binge-integrations")

include(":app")
