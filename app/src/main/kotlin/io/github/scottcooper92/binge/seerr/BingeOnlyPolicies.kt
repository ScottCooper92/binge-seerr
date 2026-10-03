package io.github.scottcooper92.binge.seerr

import android.util.Log
import com.binge.companion.sdk.BingeHosts
import io.grpc.Status
import io.grpc.binder.SecurityPolicy

/** The package names the debug policies admit: debug and release Binge. */
internal val BINGE_PACKAGE_NAMES: Set<String> = setOf(BingeHosts.RELEASE_PACKAGE_NAME, BingeHosts.DEBUG_PACKAGE_NAME)

/**
 * The debug build's caller check: Binge's package names under any certificate (#679).
 *
 * A debug build can't pin debug Binge's certificate, since each developer signs with their own key, but it
 * can still require Binge's package name. Without that, the debug APK sent to Firebase testers would hand its
 * Seerr session, possibly a server-admin API key, to any app on the tester's phone. Interim until the SDK's
 * own `HostPolicy.anyCertificateOf` and `HandOffPolicy.anyCertificateOf` (binge-companions#125) are pinned
 * here; then both of these go and the SDK's are used instead.
 *
 * The app's own uid is also admitted, so the device lane's round-trip test can bind from inside the app under
 * test. That uid is this app, so no other app gets in by it.
 */
internal class BingeOnlyHostPolicy(
    private val selfUid: Int,
    private val packagesForUid: (uid: Int) -> List<String>,
    private val warn: (message: String) -> Unit = {},
) : SecurityPolicy() {
    override fun checkAuthorization(uid: Int): Status {
        if (uid == selfUid) return Status.OK
        val host =
            packagesForUid(uid).firstOrNull { it in BINGE_PACKAGE_NAMES }
                ?: return Status.PERMISSION_DENIED.withDescription("uid $uid is not a Binge package")
        warn("Admitting $host (uid=$uid) without verifying its certificate (debug build)")
        return Status.OK
    }
}

/** Who a hand-off Activity acts for: `Activity.callingPackage` in, a yes or no out. */
internal fun interface HandOffGate {
    fun permits(callingPackage: String?): Boolean
}

/** The hand-off Activities' debug gate: a Binge package that started them for a result, any certificate. */
internal fun bingeOnlyHandOffGate(warn: (message: String) -> Unit = {}): HandOffGate =
    HandOffGate { callingPackage ->
        (callingPackage != null && callingPackage in BINGE_PACKAGE_NAMES).also { admitted ->
            if (admitted) warn("Admitting hand-off from $callingPackage without verifying its certificate (debug build)")
        }
    }

internal fun logWarning(tag: String): (String) -> Unit = { message -> Log.w(tag, message) }
