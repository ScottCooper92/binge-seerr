package com.cooper.binge.companion.seerr

import android.util.Log
import io.grpc.Status
import io.grpc.binder.SecurityPolicy

/**
 * Whether this build admits callers without verifying their certificate: true on debug, where the SDK's
 * `anyCertificateOf` policies stand in for the pinned ones. One switch, so the hub's banner (#679) cannot disagree with the policy
 * the Service and the hand-off Activities actually run. Read from [BuildConfig.DEBUG] rather than from
 * anything that can be switched on in a shipped build.
 */
internal val ADMITS_UNVERIFIED_CALLERS: Boolean = BuildConfig.DEBUG

/**
 * The debug Service's caller check: [policy], plus this app's own uid (#679, #1052).
 *
 * [policy] is the SDK's `HostPolicy.anyCertificateOf`. Each developer signs debug Binge with their own key,
 * so its certificate can't be pinned, but its package name can: a debug APK on a tester's phone still keeps
 * its Seerr session from other apps.
 *
 * Its own uid lets the device lane's round-trip test bind from inside the app under test.
 */
internal class SelfOrPolicy(
    private val selfUid: Int,
    private val policy: SecurityPolicy,
) : SecurityPolicy() {
    override fun checkAuthorization(uid: Int): Status = if (uid == selfUid) Status.OK else policy.checkAuthorization(uid)
}

/** Who a hand-off Activity acts for: `Activity.callingPackage` in, a yes or no out. */
internal fun interface HandOffGate {
    fun permits(callingPackage: String?): Boolean
}

internal fun logWarning(tag: String): (String) -> Unit = { message -> Log.w(tag, message) }
