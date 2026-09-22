package io.github.scottcooper92.binge.seerr.telemetry

import com.posthog.PostHogInterface
import com.posthog.android.PostHogAndroidConfig
import javax.inject.Inject
import javax.inject.Singleton

/** What this app reports about its own use. Nothing about the server, its titles, or its users. */
interface Analytics {
    /** A screen was shown; [name] is one of this app's fixed screen names, never an id. */
    fun screen(name: String)
}

/** The three calls this app makes of an analytics SDK, so the gating around them can be tested. */
interface AnalyticsClient {
    fun optIn()

    fun optOut()

    fun screen(name: String)
}

/** [AnalyticsClient] over PostHog's own client. */
class PostHogClient(
    private val postHog: PostHogInterface,
) : AnalyticsClient {
    override fun optIn() = postHog.optIn()

    override fun optOut() = postHog.optOut()

    override fun screen(name: String) = postHog.screen(name)
}

/**
 * PostHog behind the consent gate. [client] is null when no project key was built in, and then
 * every call is a no-op.
 *
 * The client starts opted out, and is closed again here if the gate is shut: PostHog keeps its own
 * opt-in across launches, and the gate never tells a backend to close before the user has answered.
 * Registering after that means a grant opens it, and a later decline closes it.
 */
@Singleton
class PostHogAnalytics
    @Inject
    constructor(
        private val client: AnalyticsClient?,
        private val gate: AnalyticsConsentGate,
    ) : Analytics {
        init {
            if (!gate.isGranted) client?.optOut()
            gate.register { granted -> if (granted) client?.optIn() else client?.optOut() }
        }

        override fun screen(name: String) {
            if (!gate.isGranted) return
            client?.screen(name)
        }
    }

/**
 * PostHog's config with this app's privacy defaults. It starts opted out, and consent opens it.
 * Screen views are logged by name, so the SDK's own, which would name only the Activity, are off.
 * Deep links are off because a notification's link carries the server's ids.
 */
fun postHogConfig(
    apiKey: String,
    host: String,
): PostHogAndroidConfig =
    PostHogAndroidConfig(apiKey = apiKey, host = host).apply {
        optOut = true
        captureScreenViews = false
        captureDeepLinks = false
    }
