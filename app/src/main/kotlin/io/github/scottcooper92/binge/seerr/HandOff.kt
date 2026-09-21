package io.github.scottcooper92.binge.seerr

import android.content.Context
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HandOffCallerPolicy
import com.binge.companion.sdk.HandOffPolicy

private const val HAND_OFF_TAG = "SeerrCompanion"

/**
 * Who this app takes a hand-off from, in one place: both exported Activities ask the same question
 * and must not drift apart on the answer.
 *
 * Debug-permissive because a debug Binge is signed with a key no allowlist can name, pinned in
 * release, and selected by [BuildConfig.DEBUG] rather than by anything that can be switched on in
 * a shipped build.
 */
internal fun Context.bingeHandOffPolicy(): HandOffCallerPolicy =
    if (BuildConfig.DEBUG) {
        HandOffPolicy.anyCaller(HAND_OFF_TAG)
    } else {
        HandOffPolicy.pinned(this, listOf(BingeHosts.release))
    }
