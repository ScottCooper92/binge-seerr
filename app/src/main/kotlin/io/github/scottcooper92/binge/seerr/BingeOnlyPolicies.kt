package io.github.scottcooper92.binge.seerr

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
 * [policy] is the SDK's `HostPolicy.anyCertificateOf`: Binge's package names under any certificate. A debug
 * build can't pin debug Binge's certificate, since each developer signs with their own key, but it can still
 * require Binge's package name, so the debug APK sent to Firebase testers does not hand its Seerr session to
 * any app on the tester's phone.
 *
 * The app's own uid is admitted on top, so the device lane's round-trip test can bind from inside the app
 * under test. That uid is this app, so no other app gets in by it. Everything else is the SDK's decision.
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
