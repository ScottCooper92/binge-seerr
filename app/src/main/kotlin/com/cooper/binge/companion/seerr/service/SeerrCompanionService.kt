package com.cooper.binge.companion.seerr.service

import android.os.Process
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HostPolicy
import com.binge.companion.sdk.IntegrationService
import com.cooper.binge.companion.seerr.ADMITS_UNVERIFIED_CALLERS
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.SelfOrPolicy
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.MediaStatusStore
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.notifications.NotificationForwarding
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.telemetry.Analytics
import dagger.hilt.android.AndroidEntryPoint
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

    @Inject
    lateinit var forwarding: NotificationForwarding

    @Inject
    lateinit var notificationPrefs: NotificationPrefs

    override fun services(): List<BindableService> =
        listOf(
            SeerrRequestService(
                connection,
                BuildConfig.VERSION_NAME,
                statusCache = statuses,
                bingeConnection = bingeConnection,
                analytics = analytics,
                requestCache = requests,
                userMessages = AndroidUserMessages(this),
            ),
            SeerrNotificationsService(SeerrNotificationRpcs(forwarding, notificationPrefs)),
        )

    /**
     * Debug builds admit only Binge's package names, under any certificate: a debug Binge is signed with its
     * developer's own key, which no allowlist can name, but the debug APK goes to Firebase testers, so it must
     * not hand the session to any app on their phone (#679). Release builds pin `BingeHosts.release`, Binge's
     * published Play App Signing certificate, and refuse any other signer.
     */
    override fun hostPolicy(): SecurityPolicy =
        if (ADMITS_UNVERIFIED_CALLERS) {
            SelfOrPolicy(selfUid = Process.myUid(), policy = HostPolicy.anyCertificateOf(this, tag = TAG))
        } else {
            HostPolicy.pinned(this, listOf(BingeHosts.release))
        }

    private companion object {
        const val TAG = "SeerrCompanion"
    }
}
