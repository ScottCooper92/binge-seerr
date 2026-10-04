package io.github.scottcooper92.binge.seerr.service

import android.os.Process
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HostPolicy
import com.binge.companion.sdk.IntegrationService
import dagger.hilt.android.AndroidEntryPoint
import io.github.scottcooper92.binge.seerr.BingeOnlyHostPolicy
import io.github.scottcooper92.binge.seerr.BuildConfig
import io.github.scottcooper92.binge.seerr.auth.BingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.MediaStatusStore
import io.github.scottcooper92.binge.seerr.data.RequestStore
import io.github.scottcooper92.binge.seerr.logWarning
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.grpc.BindableService
import io.grpc.binder.SecurityPolicy
import javax.inject.Inject

/**
 * The exported Service Binge binds to. Everything about hosting a gRPC server on Binder is the
 * SDK's; what is this app's is which contracts it serves and who it lets in.
 */
@AndroidEntryPoint
class SeerrCompanionService : IntegrationService() {
    /** Injected before the SDK's `onCreate` asks for [services], which is when the server is built. */
    @Inject
    lateinit var connection: SeerrConnection

    /** Survives this process, which is the point: it is bound and reclaimed far more often than the app is opened. */
    @Inject
    lateinit var statuses: MediaStatusStore

    @Inject
    lateinit var requests: RequestStore

    @Inject
    lateinit var bingeConnection: BingeConnectionStore

    @Inject
    lateinit var analytics: Analytics

    override fun services(): List<BindableService> =
        listOf(
            SeerrRequestService(
                connection,
                BuildConfig.VERSION_NAME,
                statusCache = statuses,
                bingeConnection = bingeConnection,
                analytics = analytics,
                requestCache = requests,
            ),
        )

    /**
     * Debug builds admit only Binge's package names, under any certificate: a debug Binge is signed with its
     * developer's own key, which no allowlist can name, but the debug APK goes to Firebase testers, so it must
     * not hand the session to any app on their phone (#679). Release builds pin `BingeHosts.release`, Binge's
     * published Play App Signing certificate, and refuse any other signer.
     */
    override fun hostPolicy(): SecurityPolicy =
        if (BuildConfig.DEBUG) {
            BingeOnlyHostPolicy(
                selfUid = Process.myUid(),
                packagesForUid = { uid -> packageManager.getPackagesForUid(uid).orEmpty().toList() },
                warn = logWarning(TAG),
            )
        } else {
            HostPolicy.pinned(this, listOf(BingeHosts.release))
        }

    private companion object {
        const val TAG = "SeerrCompanion"
    }
}
