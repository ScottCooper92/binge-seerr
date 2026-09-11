package io.github.scottcooper92.binge.seerr

/**
 * Placeholder for the companion this repository will become.
 *
 * The extraction (roadmap stage 4 in binge-integrations) replaces this with the grpc-binder
 * service that serves REQUEST v1. Until then it exists so the module compiles and CI has
 * something real to build and test — the agent workflows all hang off a successful CI run,
 * and a repository with no build gives them nothing to trigger on.
 */
object SeerrCompanion {
    /**
     * The REQUEST contract major version this companion serves.
     *
     * Declared as a single constant deliberately. The host negotiates capability by version,
     * so this is the one fact about the contract that belongs in the companion rather than in
     * the generated stubs, and it wants a single place to change.
     */
        const val REQUEST_CONTRACT_VERSION: Int = 1
}
