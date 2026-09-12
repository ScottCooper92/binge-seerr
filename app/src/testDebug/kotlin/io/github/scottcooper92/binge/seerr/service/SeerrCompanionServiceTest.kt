package io.github.scottcooper92.binge.seerr.service

import com.binge.integration.sdk.HostSecurityPolicy
import com.binge.integration.sdk.IntegrationService
import io.github.scottcooper92.binge.seerr.BuildConfig
import io.grpc.binder.SecurityPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Debug-only: `hostPolicy()` branches on `BuildConfig.DEBUG`, so this only means anything built
 * against the debug variant. `hostPolicy()` is protected on the SDK's `IntegrationService` for
 * every companion, this one included, so reaching it here means reflection rather than a cast.
 */
class SeerrCompanionServiceTest {
    @Test
    fun `a debug build's policy is not the pinned one`() {
        assertTrue(BuildConfig.DEBUG)

        val hostPolicy = IntegrationService::class.java.getDeclaredMethod("hostPolicy")
        hostPolicy.isAccessible = true
        val policy = hostPolicy.invoke(SeerrCompanionService()) as SecurityPolicy

        assertFalse(policy is HostSecurityPolicy)
    }
}
