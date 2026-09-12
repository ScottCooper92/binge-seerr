package io.github.scottcooper92.binge.seerr.service

import com.binge.integration.sdk.BingeHosts
import com.binge.integration.sdk.HostPolicy
import com.binge.integration.sdk.IntegrationService
import io.github.scottcooper92.binge.seerr.BuildConfig
import io.github.scottcooper92.binge.seerr.SeerrApp
import io.grpc.BindableService
import io.grpc.binder.SecurityPolicy

/**
 * The exported Service Binge binds to. Everything about hosting a gRPC server on Binder is the
 * SDK's; what is this app's is which contracts it serves and who it lets in.
 */
class SeerrCompanionService : IntegrationService() {
    override fun services(): List<BindableService> =
        listOf(SeerrRequestService((application as SeerrApp).connection, BuildConfig.VERSION_NAME))

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
