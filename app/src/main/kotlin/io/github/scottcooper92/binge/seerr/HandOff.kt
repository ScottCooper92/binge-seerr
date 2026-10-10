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
 * a key no allowlist can name, but its package is known. [ADMITS_UNVERIFIED_CALLERS] makes the choice.
 */
internal fun Context.bingeHandOffPolicy(): HandOffGate =
    if (ADMITS_UNVERIFIED_CALLERS) {
        HandOffGate(HandOffPolicy.anyCertificateOf(tag = HAND_OFF_TAG)::permits)
    } else {
        HandOffGate(HandOffPolicy.pinned(this, listOf(BingeHosts.release))::permits)
    }
