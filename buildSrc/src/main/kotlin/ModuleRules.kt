/**
 * The module map: what each Gradle module of this build may depend on. [CheckModuleRulesTask] reads it, and a module that is not
 * in it fails the build, so adding a module means adding its row here, with the reason.
 *
 * Do not edit a row to make a build pass. A failing rule means a module reached for something it must not have, and the fix is
 * the dependency. A row changes when the design of the modules does (the table in #2163), and that change is reviewed by a person.
 */
data class ModuleRule(
    /** The Gradle path, such as `:data`. */
    val path: String,
    /** The other modules of this build it may depend on. A module reaching for one not listed here fails. */
    val projects: Set<String>,
    /**
     * Libraries that must not be anywhere in its resolved classpath, directly or through another library: a `group:module`
     * coordinate, or a prefix of one ending in `*`.
     */
    val forbidden: List<String> = emptyList(),
    /** Why the module is held to this, said in the failure message so the rule explains itself. */
    val reason: String,
)

val MODULE_RULES: List<ModuleRule> =
    listOf(
        ModuleRule(
            path = ":app",
            projects = setOf(":data", ":core:ui"),
            reason = "The application: it wires the modules together and may depend on every module below it.",
        ),
        ModuleRule(
            path = ":data",
            projects = emptySet(),
            forbidden =
                listOf(
                    "androidx.compose*",
                    "androidx.activity*",
                    "androidx.navigation*",
                    "com.google.dagger*",
                    "com.google.android.gms*",
                    "com.google.android.play*",
                    "com.android.billingclient*",
                    "org.jetbrains.kotlinx:kotlinx-coroutines-play-services",
                    "com.google.firebase*",
                    "com.posthog*",
                    "io.coil-kt*",
                    "com.binge:designsystem*",
                ),
            reason =
                "The Seerr client, the connection, the demo and the cache: no Compose, Activity, navigation or design system (no UI), " +
                    "no Hilt or Dagger (the graph is built in :app), and no vendor SDK (Play services, Play Billing, Play In-App Review, Firebase, PostHog).",
        ),
        ModuleRule(
            path = ":core:ui",
            projects = setOf(":data"),
            forbidden =
                listOf(
                    "com.google.dagger*",
                    "com.google.android.gms*",
                    "com.google.android.play*",
                    "com.android.billingclient*",
                    "org.jetbrains.kotlinx:kotlinx-coroutines-play-services",
                    "com.google.firebase*",
                    "com.posthog*",
                ),
            reason =
                "The screen pieces every feature shares: the design system and the Seerr error types, and no feature. No Hilt or Dagger " +
                    "(the graph is built in :app) and no vendor SDK (Play services, Play Billing, Play In-App Review, Firebase, PostHog).",
        ),
        ModuleRule(
            path = ":core:testing",
            projects = setOf(":data"),
            reason = "What the tests of every module share: it reaches the code under test, which is :data, and nothing above it.",
        ),
        ModuleRule(
            path = ":detekt-rules",
            projects = emptySet(),
            reason = "The comment rules detekt loads: a build tool, which depends on no module of the app.",
        ),
    )

fun ModuleRule.forbids(coordinate: String): Boolean =
    forbidden.any { pattern -> if (pattern.endsWith("*")) coordinate.startsWith(pattern.dropLast(1)) else coordinate == pattern }
