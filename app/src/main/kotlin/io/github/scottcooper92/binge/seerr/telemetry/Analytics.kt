package io.github.scottcooper92.binge.seerr.telemetry

import com.posthog.PostHogInterface
import com.posthog.android.PostHogAndroidConfig
import io.github.scottcooper92.binge.seerr.notifications.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
 *
 * A screen shown before the stored answer is read is held, not dropped: on a cold start the first
 * screen arrives before the read does, and would otherwise go missing on every launch.
 */
@Singleton
class PostHogAnalytics
    @Inject
    constructor(
        private val client: AnalyticsClient?,
        private val gate: AnalyticsConsentGate,
        @ApplicationScope scope: CoroutineScope,
    ) : Analytics {
        private val lock = Any()
        private val held = mutableListOf<String>()
        private var read = false

        init {
            if (!gate.isGranted) client?.optOut()
            gate.register { granted -> if (granted) client?.optIn() else client?.optOut() }
            if (client != null) {
                scope.launch {
                    gate.awaitRead()
                    release()
                }
            }
        }

        override fun screen(name: String) {
            if (client == null) return
            synchronized(lock) {
                if (!read) {
                    if (held.size < MAX_HELD_SCREENS) held += name
                    return
                }
            }
            send(name)
        }

        private fun release() {
            val names =
                synchronized(lock) {
                    read = true
                    held.toList().also { held.clear() }
                }
            names.forEach(::send)
        }

        private fun send(name: String) {
            if (gate.isGranted) client?.screen(name)
        }

        private companion object {
            /** A cold start shows one or two screens before the read lands; this only bounds a stuck read. */
            const val MAX_HELD_SCREENS = 10
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
