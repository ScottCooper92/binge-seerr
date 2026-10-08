// The kotlin-dsl plugin's own dependencies come through here: the mirror first, as everywhere in this build.
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
}

// buildSrc reads the root catalog so its test dependencies move with the app's.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../libs.versions.toml")) }
    }
}
