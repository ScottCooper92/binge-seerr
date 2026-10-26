package com.cooper.binge.companion.seerr

import android.content.Context
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HandOffPolicy
import com.cooper.binge.companion.seerr.flags.FeatureFlags

private const val HAND_OFF_TAG = "SeerrCompanion"

/**
 * Who this app takes a hand-off from, in one place: every exported hand-off Activity asks the same question
 * and must not drift apart on the answer.
 *
 * Pinned in release. In debug, Binge's package names under any certificate (#679): a debug Binge is signed with
 * a key no allowlist can name, but its package is known. [ADMITS_UNVERIFIED_CALLERS] makes the choice.
 *
 * While `request_companions_enabled` is off it admits nobody, so a hand-off finishes as an unadmitted caller's does.
 */
internal fun Context.bingeHandOffPolicy(featureFlags: FeatureFlags): HandOffGate {
    val callers =
        if (ADMITS_UNVERIFIED_CALLERS) {
            HandOffPolicy.anyCertificateOf(tag = HAND_OFF_TAG)::permits
        } else {
            HandOffPolicy.pinned(this, listOf(BingeHosts.release))::permits
        }
    return HandOffGate { caller -> featureFlags.flags.value.requestCompanions && callers(caller) }
}
