// The build-logic types this repository carries: the translation-staleness task, ported from Binge's
// convention plugin, and the gRPC alignment check. Everything else configures the app module directly.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}
