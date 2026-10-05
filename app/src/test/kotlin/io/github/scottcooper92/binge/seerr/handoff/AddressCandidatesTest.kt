package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.seerr.SeerrPublicSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the phone offers to send: normalised, de-duplicated, best first. */
class AddressCandidatesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `normalising lowercases scheme and host, drops a default port and user info, and ends in a slash`() {
        assertEquals("http://seerr.lan:5055/", normaliseServerAddress("HTTP://Seerr.LAN:5055"))
        assertEquals("https://seerr.example.com/", normaliseServerAddress("https://seerr.example.com:443/"))
        assertEquals("http://192.168.1.10/", normaliseServerAddress("http://admin:pw@192.168.1.10:80/?x=1#y"))
        assertEquals("http://192.168.1.10:5055/", normaliseServerAddress("192.168.1.10:5055"))
        assertEquals("https://example.com/seerr/", normaliseServerAddress("https://example.com/seerr"))
    }

    @Test
    fun `normalising refuses what the TV's form refuses`() {
        listOf("", "   ", "http://", "http://:5055", "ht tp://x").forEach { assertNull(it, normaliseServerAddress(it)) }
    }

    @Test
    fun `two spellings of one server are offered once`() {
        val candidates =
            addressCandidates(
                connected = "http://192.168.1.10:5055/",
                applicationUrl = "HTTP://192.168.1.10:5055",
                remembered = listOf("http://192.168.1.10:5055"),
            )

        assertEquals(listOf(AddressCandidate("http://192.168.1.10:5055/", AddressSource.Remembered, AddressLocality.Local)), candidates)
    }

    @Test
    fun `a local address comes first, then a name, then an IP that is not local`() {
        val candidates =
            addressCandidates(
                connected = "http://100.101.102.103:5055/",
                applicationUrl = "https://seerr.example.com",
                remembered = listOf("http://192.168.1.10:5055"),
            )

        assertEquals(
            listOf("http://192.168.1.10:5055/", "https://seerr.example.com/", "http://100.101.102.103:5055/"),
            candidates.map { it.address },
        )
        assertEquals(
            listOf(AddressLocality.Local, AddressLocality.Unknown, AddressLocality.NotLocal),
            candidates.map { it.locality },
        )
    }

    @Test
    fun `within one locality a typed address beats the Application URL, which beats the phone's own`() {
        val candidates =
            addressCandidates(
                connected = "http://192.168.1.10:5055/",
                applicationUrl = "http://seerr.local:5055",
                remembered = listOf("http://nas:5055", "http://10.0.0.4:5055"),
            )

        assertEquals(
            listOf(
                AddressSource.Remembered,
                AddressSource.Remembered,
                AddressSource.ApplicationUrl,
                AddressSource.Connected,
            ),
            candidates.map { it.source },
        )
        assertEquals("http://nas:5055/", candidates.first().address)
    }

    @Test
    fun `a not-local address is never first while there is another`() {
        val candidates =
            addressCandidates(
                connected = "https://seerr.example.com/",
                applicationUrl = "http://100.64.0.7:5055",
                remembered = listOf("http://[fd7a:115c:a1e0::9]:5055"),
            )

        assertEquals(AddressSource.Connected, candidates.first().source)
        assertEquals(1, addressCandidates("http://100.64.0.7:5055/", null, emptyList()).size)
    }

    @Test
    fun `an Application URL is read from settings, and blank or missing is none`() {
        fun read(body: String) = json.decodeFromString<SeerrPublicSettings>(body).applicationUrlOrNull()

        assertEquals("https://seerr.example.com/", read("""{"applicationTitle":"Seerr","applicationUrl":"https://seerr.example.com"}"""))
        assertNull(read("""{"applicationTitle":"Seerr","applicationUrl":""}"""))
        assertNull(read("""{"applicationTitle":"Seerr","applicationUrl":"   "}"""))
        assertNull(read("""{"applicationTitle":"Seerr"}"""))
        assertNull(read("""{"applicationUrl":null}"""))
    }

    @Test
    fun `the other-address field starts with the connected scheme and any port it named`() {
        assertEquals("http://:5055", otherAddressPrefill("http://192.168.1.10:5055/"))
        assertEquals("https://", otherAddressPrefill("https://seerr.example.com/"))
        assertEquals("http://", otherAddressPrefill("http://nas/"))
        assertEquals("", otherAddressPrefill("not a url"))
    }
}
