package io.github.scottcooper92.binge.seerr

import android.content.Context
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HandOffPolicy

private const val HAND_OFF_TAG = "SeerrCompanion"

/**
 * Who this app takes a hand-off from, in one place: both exported Activities ask the same question
 * and must not drift apart on the answer.
 *
 * Pinned in release. In debug, Binge's package names under any certificate (#679): a debug Binge is signed with
 * a key no allowlist can name, but its package is known. Selected by [BuildConfig.DEBUG] rather than by anything
 * that can be switched on in a shipped build.
 */
internal fun Context.bingeHandOffPolicy(): HandOffGate =
    if (BuildConfig.DEBUG) {
        bingeOnlyHandOffGate(logWarning(HAND_OFF_TAG))
    } else {
        val pinned = HandOffPolicy.pinned(this, listOf(BingeHosts.release))
        HandOffGate(pinned::permits)
    }
