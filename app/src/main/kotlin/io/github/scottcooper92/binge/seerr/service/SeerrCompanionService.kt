package io.github.scottcooper92.binge.seerr.service

import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HostPolicy
import com.binge.companion.sdk.IntegrationService
import dagger.hilt.android.AndroidEntryPoint
import io.github.scottcooper92.binge.seerr.BuildConfig
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.MediaStatusStore
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

    override fun services(): List<BindableService> =
        listOf(SeerrRequestService(connection, BuildConfig.VERSION_NAME, statusCache = statuses))

    /**
     * Debug builds admit any caller, because a debug Binge is signed with its developer's own key
     * and no allowlist can name it. Release builds pin Binge's published certificate, and until
     * that digest is published in the SDK they admit nobody: fail closed, not open.
     */
    override fun hostPolicy(): SecurityPolicy =
        if (BuildConfig.DEBUG) HostPolicy.anyCaller(TAG) else HostPolicy.pinned(this, listOf(BingeHosts.release))

    private companion object {
        const val TAG = "SeerrCompanion"
    }
}
