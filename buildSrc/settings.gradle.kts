// buildSrc reads the root catalog so its test dependencies move with the app's.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../libs.versions.toml")) }
    }
}
