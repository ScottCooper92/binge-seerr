package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyAndDvrKeyRulesTest {
    private fun proxy(
        user: String = "",
        password: String = "",
        enabled: Boolean = true,
    ) = ProxyForm(enabled = enabled, host = "proxy.local", port = "8080", user = user, password = password)

    @Test
    fun `a proxy with neither credential or both is valid`() {
        assertTrue(proxy().valid)
        assertTrue(proxy(user = "scott", password = "secret").valid)
    }

    @Test
    fun `a username without a password is flagged on the password`() {
        val form = proxy(user = "scott")
        assertTrue(form.passwordMissing)
        assertFalse(form.userMissing)
        assertFalse(form.valid)
    }

    @Test
    fun `a password without a username is flagged on the username`() {
        val form = proxy(password = "secret")
        assertTrue(form.userMissing)
        assertFalse(form.passwordMissing)
        assertFalse(form.valid)
    }

    @Test
    fun `a blank username counts as none`() {
        assertFalse(proxy(user = "   ", password = "secret").valid)
        assertTrue(proxy(user = "   ", password = "").valid)
    }

    @Test
    fun `an unpaired credential does not block saving while the proxy is off`() {
        assertTrue(proxy(user = "scott", enabled = false).valid)
        assertTrue(NetworkForm(proxy = proxy(password = "x", enabled = false)).valid)
    }

    @Test
    fun `an unpaired credential makes the whole network form invalid`() {
        assertFalse(NetworkForm(proxy = proxy(user = "scott")).valid)
    }

    private fun instance(apiKey: String) =
        DvrForm
            .blank(ServiceType.Sonarr)
            .copy(name = "Main", host = "sonarr.local", apiKey = apiKey, profileId = 1, rootFolder = "/tv")

    @Test
    fun `a new instance cannot be saved or tested without an api key`() {
        listOf("", "   ").forEach {
            assertFalse(instance(it).connectionValid)
            assertFalse(instance(it).valid)
        }
        assertTrue(instance("key").valid)
    }
}
