package com.cooper.binge.companion.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The self-hosted server needs user CAs and cleartext; the hosts the app fixes itself get neither. */
class NetworkSecurityConfigTest {
    private val root =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/network_security_config.xml"))
            .documentElement

    private fun Element.children(tag: String) =
        (0 until childNodes.length)
            .mapNotNull { childNodes.item(it) as? Element }
            .filter { it.tagName == tag }

    private fun Element.anchors() =
        children("trust-anchors")
            .single()
            .children("certificates")
            .map { it.getAttribute("src") }

    @Test
    fun `the fixed hosts are system trust only and never cleartext`() {
        val config = root.children("domain-config").single()
        assertEquals("false", config.getAttribute("cleartextTrafficPermitted"))
        assertEquals(listOf("system"), config.anchors())
        val hosts = config.children("domain").map { it.textContent.trim() }
        assertTrue("plex.tv" in hosts)
        assertTrue(config.children("domain").all { it.getAttribute("includeSubdomains") == "true" })
    }

    @Test
    fun `the self-hosted server's default still trusts user CAs and allows a LAN address`() {
        val base = root.children("base-config").single()
        assertEquals("true", base.getAttribute("cleartextTrafficPermitted"))
        assertTrue("user" in base.anchors())
        assertFalse(base.anchors().isEmpty())
    }
}
