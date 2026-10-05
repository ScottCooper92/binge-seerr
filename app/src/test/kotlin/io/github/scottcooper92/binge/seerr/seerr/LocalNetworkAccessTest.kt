package io.github.scottcooper92.binge.seerr.seerr

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LocalNetworkAccessTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a private address, a home network name and a single label are local, a public host is not`() {
        assertEquals("192.168.1.10", "192.168.1.10:5055".localNetworkHostOrNull())
        assertEquals("seerr.lan", "seerr.lan".localNetworkHostOrNull())
        assertEquals("nas", "http://nas:5055".localNetworkHostOrNull())
        assertNull("seerr.example.com".localNetworkHostOrNull())
        assertNull("https://seerr.example.com".localNetworkHostOrNull())
    }

    @Test
    fun `an address that does not parse needs nothing`() {
        assertNull("http://".localNetworkHostOrNull())
        assertNull("".localNetworkHostOrNull())
    }

    @Test
    fun `an address is blocked only when it is local and the permission is refused`() {
        val refused = LocalNetworkPermission { false }
        val granted = LocalNetworkPermission { true }

        assertTrue("192.168.1.10".isBlockedByLocalNetwork(refused))
        assertFalse("192.168.1.10".isBlockedByLocalNetwork(granted))
        assertFalse("seerr.example.com".isBlockedByLocalNetwork(refused))
    }

    @Test
    fun `below the SDK that has the permission nothing is ever refused`() {
        assertTrue(AndroidLocalNetworkPermission(app, sdkInt = 36).isGranted())
    }

    @Test
    fun `on the SDK that has the permission, the answer is the runtime grant`() {
        assertFalse(AndroidLocalNetworkPermission(app, sdkInt = 37).isGranted())

        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)

        assertTrue(AndroidLocalNetworkPermission(app, sdkInt = 37).isGranted())
    }
}
