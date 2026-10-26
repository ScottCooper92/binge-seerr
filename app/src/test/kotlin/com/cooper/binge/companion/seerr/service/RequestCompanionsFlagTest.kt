package com.cooper.binge.companion.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.notifications.v1.SetNotificationForwardingRequest
import com.binge.companion.contracts.request.v1.GetAttentionRequest
import com.binge.companion.contracts.request.v1.GetStatusRequest
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.ListRequestsRequest
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.NoBingeConnectionStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.flags.FixedFeatureFlags
import com.cooper.binge.companion.seerr.flags.Flags
import com.cooper.binge.companion.seerr.notifications.NotificationForwarding
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.util.PlainCipher
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `request_companions_enabled` off: the Service answers the handshake with nothing to offer and serves no rpc. */
class RequestCompanionsFlagTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class CountingBingeStore : BingeConnectionStore by NoBingeConnectionStore {
        var handshakes = 0

        override suspend fun recordHandshake() {
            handshakes++
        }
    }

    private val binge = CountingBingeStore()

    private fun service(flags: Flags): SeerrRequestService {
        val store = CredentialStore(PreferenceDataStoreFactory.create { folder.newFile("creds.preferences_pb") }, PlainCipher)
        return SeerrRequestService(
            SeerrConnection(store, SeerrApiFactory(logRequests = false)),
            versionName = "0.1.0-test",
            bingeConnection = binge,
            featureFlags = FixedFeatureFlags(flags),
        )
    }

    private suspend fun assertDenied(call: suspend () -> Unit) {
        try {
            call()
            fail("expected PERMISSION_DENIED")
        } catch (e: StatusException) {
            assertEquals(Status.Code.PERMISSION_DENIED, e.status.code)
        }
    }

    @Test
    fun `the handshake declares nothing and is not recorded`() =
        runTest {
            val response = service(Flags()).handshake(HandshakeRequest.getDefaultInstance())

            assertTrue(response.capabilitiesList.isEmpty())
            assertTrue(response.versionsList.isEmpty())
            assertEquals(0, binge.handshakes)
        }

    @Test
    fun `no rpc is served, whatever the capability it needs`() =
        runTest {
            val service = service(Flags(bingePromotion = true))

            assertDenied { service.submitRequest(SubmitRequestRequest.getDefaultInstance()) }
            assertDenied { service.getStatus(GetStatusRequest.getDefaultInstance()) }
            assertDenied { service.getAttention(GetAttentionRequest.getDefaultInstance()) }
            assertDenied { service.listRequests(ListRequestsRequest.getDefaultInstance()) }
        }

    @Test
    fun `forwarding can be switched off but not on`() =
        runTest {
            val store = PreferenceDataStoreFactory.create { folder.newFile("n.preferences_pb") }
            val flags = FixedFeatureFlags(Flags())
            val forwarding = NotificationForwarding(store, featureFlags = flags)
            val rpcs = SeerrNotificationRpcs(forwarding, NotificationPrefs(store), flags)

            assertDenied { rpcs.setNotificationForwarding(SetNotificationForwardingRequest.newBuilder().setEnabled(true).build()) }
            rpcs.setNotificationForwarding(SetNotificationForwardingRequest.newBuilder().setEnabled(false).build())
            assertFalse(forwarding.isActive())
        }
}
