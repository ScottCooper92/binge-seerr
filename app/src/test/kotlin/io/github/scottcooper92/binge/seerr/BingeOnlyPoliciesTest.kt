package io.github.scottcooper92.binge.seerr

import com.binge.companion.sdk.BingeHosts
import io.grpc.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug build admits Binge's packages under any certificate, and refuses every other app (#679). */
class BingeOnlyPoliciesTest {
    private val uid = 10_042
    private val selfUid = 10_001

    private fun hostCode(vararg packages: String): Status.Code =
        BingeOnlyHostPolicy(selfUid = selfUid, packagesForUid = { packages.toList() }).checkAuthorization(uid).code

    @Test
    fun `the Service admits debug and release Binge`() {
        assertEquals(Status.Code.OK, hostCode(BingeHosts.DEBUG_PACKAGE_NAME))
        assertEquals(Status.Code.OK, hostCode(BingeHosts.RELEASE_PACKAGE_NAME))
    }

    @Test
    fun `the Service refuses any other app, and a uid with no package`() {
        assertEquals(Status.Code.PERMISSION_DENIED, hostCode("com.example.other"))
        assertEquals(Status.Code.PERMISSION_DENIED, hostCode())
    }

    @Test
    fun `the Service admits its own uid and still refuses another uid with a non-Binge package`() {
        val policy = BingeOnlyHostPolicy(selfUid = selfUid, packagesForUid = { listOf("com.example.other") })

        assertEquals(Status.Code.OK, policy.checkAuthorization(selfUid).code)
        assertEquals(Status.Code.PERMISSION_DENIED, policy.checkAuthorization(uid).code)
    }

    @Test
    fun `the hand-off gate admits Binge and refuses everything else, a missing caller included`() {
        val gate = bingeOnlyHandOffGate()

        assertTrue(gate.permits(BingeHosts.DEBUG_PACKAGE_NAME))
        assertTrue(gate.permits(BingeHosts.RELEASE_PACKAGE_NAME))
        assertFalse(gate.permits("com.example.other"))
        assertFalse(gate.permits(null))
    }
}
