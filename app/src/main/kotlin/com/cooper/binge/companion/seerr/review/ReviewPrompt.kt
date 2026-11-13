package com.cooper.binge.companion.seerr.review

import android.app.Activity
import com.cooper.binge.companion.seerr.flags.FeatureFlags
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The review prompt: counts the things a user finished, and asks Play for its review card once they have earned it.
 * It is off until the `review_prompt_enabled` flag is on, never asks in a session that had a tip, and only ever
 * asks when the user returns to the root, never over a screen of their own making.
 */
@Singleton
class ReviewPrompt
    @Inject
    constructor(
        private val store: ReviewSignalStore,
        private val launcher: ReviewPromptLauncher,
        private val featureFlags: FeatureFlags,
        @ApplicationScope private val scope: CoroutineScope,
    ) : ReviewSignalSink {
        internal var clock: () -> Long = System::currentTimeMillis

        @Volatile private var suppressed = false

        /** Stamps the first launch, once. */
        fun start() {
            scope.launch { store.stampFirstLaunchIfUnset(clock()) }
        }

        /** The user did something real and it worked. */
        override fun recordPositiveAction() {
            scope.launch { store.incrementPositiveActionCount() }
        }

        /** No ask for the rest of this process: a tip was given, and a rating must not follow it. */
        fun suppressForThisSession() {
            suppressed = true
        }

        /**
         * Asks if the flag is on, nothing suppresses it, and the user has earned it. It runs in the caller's scope, so
         * a caller that leaves the root cancels the ask before the card is launched.
         */
        suspend fun maybeAsk(activity: Activity) {
            if (suppressed || !featureFlags.flags.value.reviewPrompt) return
            val now = clock()
            if (!ReviewPromptEligibility.isEligible(store.signals(), ReviewPromptThresholds(), now)) return
            store.recordPromptRequested(now)
            launcher.launch(activity)
        }

        /** Asks at once, whatever has been earned: for a debug build to see the card. */
        fun askNow(activity: Activity) {
            scope.launch {
                store.recordPromptRequested(clock())
                launcher.launch(activity)
            }
        }
    }
