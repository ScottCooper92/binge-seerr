package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkValidationTest {
    private val proxy = ProxyForm(enabled = true, host = "proxy.lan", port = "3128")
    private val cache = DnsCacheForm(enabled = true, minTtl = "5", maxTtl = "60")
    private val complete = NetworkForm(proxy = proxy, dnsCache = cache)

    @Test
    fun `a complete page, and one without a proxy or cache, have no issue`() {
        assertTrue(complete.issues().isEmpty())
        assertTrue(NetworkForm().issues().isEmpty())
    }

    @Test
    fun `a proxy or cache that is off has no issue whatever it holds`() {
        val off = NetworkForm(proxy = ProxyForm(port = "x", user = "ann"), dnsCache = DnsCacheForm(minTtl = "x"))

        assertTrue(off.issues().isEmpty())
    }

    @Test
    fun `an empty proxy that is on is missing its host and port`() {
        val issues = NetworkForm(proxy = ProxyForm(enabled = true)).issues()

        assertEquals(listOf(NetworkFields.PROXY_HOST, NetworkFields.PROXY_PORT), issues.map { it.field })
        assertTrue(issues.all { it.kind == EditorIssueKind.Missing && it.section == NetworkSections.PROXY })
    }

    @Test
    fun `a port out of range is invalid`() {
        val issue = complete.copy(proxy = proxy.copy(port = "70000")).issues().single()

        assertEquals(EditorIssueKind.Invalid, issue.kind)
        assertEquals(R.string.editor_error_port, issue.messageRes)
    }

    @Test
    fun `half a credential pair is reported at once on the half that is missing`() {
        assertEquals(
            NetworkFields.PROXY_USER,
            complete
                .copy(proxy = proxy.copy(password = "p"))
                .issues()
                .single()
                .field,
        )
        assertEquals(
            NetworkFields.PROXY_PASSWORD,
            complete
                .copy(proxy = proxy.copy(user = "u"))
                .issues()
                .single()
                .field,
        )
    }

    @Test
    fun `crossed ttls are reported on the maximum`() {
        val issue = complete.copy(dnsCache = cache.copy(minTtl = "60", maxTtl = "5")).issues().single()

        assertEquals(NetworkFields.MAX_TTL, issue.field)
        assertEquals(R.string.server_settings_dns_ttl_order, issue.messageRes)
    }

    @Test
    fun `no issues exactly when the form is valid`() {
        val drafts =
            listOf(
                complete,
                NetworkForm(),
                complete.copy(proxy = proxy.copy(host = "")),
                complete.copy(proxy = proxy.copy(port = "")),
                complete.copy(proxy = proxy.copy(port = "0")),
                complete.copy(proxy = proxy.copy(user = "u")),
                complete.copy(proxy = proxy.copy(password = "p")),
                complete.copy(proxy = proxy.copy(user = "u", password = "p")),
                complete.copy(dnsCache = cache.copy(minTtl = "-1")),
                complete.copy(dnsCache = cache.copy(maxTtl = "x")),
                complete.copy(dnsCache = cache.copy(minTtl = "x", maxTtl = "1")),
                complete.copy(dnsCache = cache.copy(minTtl = "60", maxTtl = "5")),
                complete.copy(dnsCache = cache.copy(minTtl = "", maxTtl = "")),
            )

        drafts.forEach { draft -> assertEquals("$draft", draft.valid, draft.issues().isEmpty()) }
    }
}
