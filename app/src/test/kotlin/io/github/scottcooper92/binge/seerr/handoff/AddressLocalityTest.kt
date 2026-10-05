package io.github.scottcooper92.binge.seerr.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The locality classifier: from the host as written, with nothing resolved. */
class AddressLocalityTest {
    private fun assertLocality(
        expected: AddressLocality,
        vararg hosts: String,
    ) = hosts.forEach { assertEquals(it, expected, hostLocality(it)) }

    @Test
    fun `private, link-local and loopback IPv4 are local`() =
        assertLocality(
            AddressLocality.Local,
            "10.0.0.1",
            "10.255.255.255",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.86.20",
            "169.254.1.1",
            "127.0.0.1",
        )

    @Test
    fun `any other IPv4 is not local, the CGNAT and Tailscale range included`() =
        assertLocality(
            AddressLocality.NotLocal,
            "8.8.8.8",
            "172.15.0.1",
            "172.32.0.1",
            "192.169.0.1",
            "100.64.0.1",
            "100.101.102.103",
            "100.127.255.255",
            "1.1.1.1",
        )

    @Test
    fun `IPv6 loopback, link-local and unique-local are local`() =
        assertLocality(
            AddressLocality.Local,
            "::1",
            "fe80::1",
            "febf::1",
            "fc00::1",
            "fd12:3456:789a::1",
            "[fd00::5]",
            "::ffff:192.168.1.2",
        )

    @Test
    fun `Tailscale's unique-local block and any public IPv6 are not local`() =
        assertLocality(
            AddressLocality.NotLocal,
            "fd7a:115c:a1e0::1",
            "fd7a:115c:a1e0:ab12:4843:cd96:6258:b240",
            "2001:4860:4860::8888",
            "fec0::1",
            "::ffff:8.8.8.8",
        )

    @Test
    fun `a dot-local name and a single label are local, whatever their case`() =
        assertLocality(AddressLocality.Local, "nas", "seerr.local", "Seerr.LOCAL", "nas.local.", "localhost")

    @Test
    fun `any other name is unknown, a tailnet name included`() =
        assertLocality(AddressLocality.Unknown, "seerr.example.com", "seerr.lan", "nas.tail1234.ts.net", "1.2.3.4.nip.io", "")

    @Test
    fun `an address is classified on its host, and a non-address is not classified`() {
        assertEquals(AddressLocality.Local, addressLocality("http://192.168.1.10:5055/"))
        assertEquals(AddressLocality.NotLocal, addressLocality("https://[fd7a:115c:a1e0::1]:5055"))
        assertEquals(AddressLocality.Unknown, addressLocality("seerr.example.com"))
        assertNull(addressLocality("http://"))
    }
}
