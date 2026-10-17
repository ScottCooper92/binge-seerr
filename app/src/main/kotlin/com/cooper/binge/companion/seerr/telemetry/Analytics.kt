package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.posthog.PostHogInterface
import com.posthog.android.PostHogAndroidConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this app reports about its own use, and about the failures it meets. About the server, only its
 * software and version, never its address or contents; nothing about its titles or its users.
 */
interface Analytics {
    /** A screen was shown; [name] is one of this app's fixed screen names, never an id. */
    fun screen(name: String)

    /** A write-path action committed or failed; [name] is one of [AnalyticsEvents]' fixed names, [properties] plain values only. */
    fun event(
        name: String,
        properties: Map<String, Any> = emptyMap(),
    )
}

/** Reports nothing — the default for a call site (a test, a preview) with no telemetry wired in. */
object NoOpAnalytics : Analytics {
    override fun screen(name: String) = Unit

    override fun event(
        name: String,
        properties: Map<String, Any>,
    ) = Unit
}

/** The four calls this app makes of an analytics SDK, so the gating around them can be tested. */
interface AnalyticsClient {
    fun optIn()

    fun optOut()

    fun screen(name: String)

    fun event(
        name: String,
        properties: Map<String, Any> = emptyMap(),
    )
}

/** [AnalyticsClient] over PostHog's own client. */
class PostHogClient(
    private val postHog: PostHogInterface,
) : AnalyticsClient {
    override fun optIn() = postHog.optIn()

    override fun optOut() = postHog.optOut()

    override fun screen(name: String) = postHog.screen(name)

    override fun event(
        name: String,
        properties: Map<String, Any>,
    ) = postHog.capture(event = name, properties = properties.ifEmpty { null })
}

/**
 * PostHog behind the consent gate. [client] is null when no project key was built in, and then
 * every call is a no-op.
 *
 * The client starts opted out and is closed again here if the gate is shut, because PostHog keeps its own
 * opt-in across launches. A screen shown before the stored answer is read is held, not dropped: on a cold
 * start the first screen arrives before the read does.
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

        /**
         * Unlike [screen], never held: an action event fires from a deliberate tap, which cannot land in
         * the brief cold-start window before the stored consent answer is read, so there is nothing worth
         * buffering for.
         */
        override fun event(
            name: String,
            properties: Map<String, Any>,
        ) {
            if (client == null || !gate.isGranted) return
            client.event(name, properties)
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
 * PostHog's config with this app's privacy defaults: opted out until consent. The SDK's screen views
 * (Activity names only) are off, and so are deep links, which carry the server's ids. Lifecycle events
 * stay on, and the privacy policy names them.
 *
 * Opting out does not stop the SDK fetching remote config and feature flags at setup. The app uses neither,
 * so both are off. `remoteConfig` is deprecated but still read at setup; the suppression goes when it is not.
 */
@Suppress("DEPRECATION")
fun postHogConfig(
    apiKey: String,
    host: String,
): PostHogAndroidConfig =
    PostHogAndroidConfig(apiKey = apiKey, host = host).apply {
        optOut = true
        captureScreenViews = false
        captureDeepLinks = false
        remoteConfig = false
        preloadFeatureFlags = false
    }
