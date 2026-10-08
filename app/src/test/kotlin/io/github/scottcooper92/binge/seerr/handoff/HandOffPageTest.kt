package io.github.scottcooper92.binge.seerr.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/** The page the TV serves, the language it picks, and the small HTTP pieces under the listener. */
class HandOffPageTest {
    private fun copy(language: String) =
        HandOffPageCopy(
            language = language,
            title = "Title $language",
            body = "Body <b>",
            field = "Field",
            placeholder = "http://192.168.1.10:5055",
            send = "Send",
            invalid = "Invalid & wrong",
            appBody = "App body",
            openApp = "Open \"app\"",
            install = "Install <app> from the Store",
            storeName = "Store",
            sentTitle = "Sent",
            sentBody = "Sent body",
            pinTitle = "Pin title",
            pinBody = "Pin body",
            pinField = "Pin field",
            pinSubmit = "Pin submit",
            pinWrong = "Pin wrong",
            pinLocked = "Pin locked",
            failed = "Not found <here>",
            signInTitle = "Finish",
            signInBody = { server -> "Sign in to $server" },
            connectedTitle = "Done",
            connectedBody = "Done body",
            signInFormTitle = { server -> "Sign in to $server here" },
            signInFormBody = "Sealed for your TV",
            modeField = "Sign in with",
            modeLabel = { it },
            username = "Username",
            email = "Email",
            password = "Password",
            apiKey = "API key",
            signIn = "Sign in",
            signingIn = "Signing in",
            rejected = "Rejected",
        )

    private val page = HandOffPageTemplate(copyFor = ::copy, appLink = "intent://tv-handoff?to=1.2.3.4:5&token=t#Intent;end")

    @Test
    fun `the form's install line links the store name to the release app's listing`() {
        val html = page.form(acceptLanguage = null, invalid = false)

        assertTrue(html.contains("""<p class="note">Install &lt;app&gt; from the <a href="$HAND_OFF_STORE_URL">Store</a></p>"""))
    }

    @Test
    fun `the form is self-contained, escapes its copy, and posts back to itself`() {
        val html = page.form("es-ES,es;q=0.9", invalid = false)

        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("<html lang=\"es\""))
        assertTrue(html.contains("Title es"))
        assertTrue(html.contains("Body &lt;b&gt;"))
        assertTrue(html.contains("<form method=\"post\">"))
        assertTrue(html.contains("name=\"address\""))
        assertTrue(html.contains("href=\"intent://tv-handoff?to=1.2.3.4:5&amp;token=t#Intent;end\""))
        assertTrue(html.contains("Open &quot;app&quot;"))
        // One script, the page's own, and nothing else that runs.
        assertEquals(1, Regex("<script").findAll(html).count())
        assertTrue(html.contains("<script>$PAGE_SCRIPT</script>"))
        assertTrue(html.contains("id=\"app\""))
        assertFalse(html.contains("Invalid"))
    }

    @Test
    fun `a refused address shows the error, and the sent page says so`() {
        assertTrue(page.form(null, invalid = true).contains("Invalid &amp; wrong"))
        val sent = page.status("en-GB", HandOffProgress.Checking)
        assertTrue(sent.contains("<html lang=\"en\""))
        assertTrue(sent.contains("Sent body"))
        assertFalse(sent.contains("<form"))
    }

    @Test
    fun `the page's one script is the one its content-security-policy allows, and every page carries only that one`() {
        val digest =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(PAGE_SCRIPT.toByteArray())
        assertEquals(
            "sha256-" +
                java.util.Base64
                    .getEncoder()
                    .encodeToString(digest),
            PAGE_SCRIPT_HASH,
        )

        val headers = HandOffResponse(HttpStatus.Ok, "<p>").bytes().decodeToString()
        assertTrue(headers.contains("script-src '$PAGE_SCRIPT_HASH'"))
        // The sign-in step's requests go back to the TV and nowhere else.
        assertTrue(headers.contains("connect-src 'self'"))
        assertTrue(headers.contains("default-src 'none'"))

        listOf(
            HandOffProgress.Checking,
            HandOffProgress.SignIn("Home"),
            HandOffProgress.Connected,
        ).forEach { progress ->
            val html = page.status("en", progress)
            assertEquals(1, Regex("<script").findAll(html).count())
            assertTrue(html.contains("<script>$PAGE_SCRIPT</script>"))
        }
    }

    @Test
    fun `the script reads a well-formed key from the fragment and talks only to the TV's own paths`() {
        assertTrue(PAGE_SCRIPT.contains("location.hash"))
        assertTrue(PAGE_SCRIPT.contains("{43}"))
        assertTrue(PAGE_SCRIPT.contains("history.replaceState"))
        assertEquals(setOf("\"/s/\"", "\"/c/\""), Regex("fetch\\((\"/[a-z]/\")").findAll(PAGE_SCRIPT).map { it.groupValues[1] }.toSet())
        assertFalse(PAGE_SCRIPT.contains("XMLHttpRequest"))
        assertFalse(PAGE_SCRIPT.contains("document.cookie"))
    }

    @Test
    fun `the page follows the TV, asking the browser for itself again until the connection is made`() {
        val waiting = page.status("en", HandOffProgress.Waiting)
        assertTrue(waiting.contains("<form method=\"post\">"))
        assertFalse(waiting.contains("http-equiv=\"refresh\""))

        val checking = page.status("en", HandOffProgress.Checking)
        assertTrue(checking.contains("Sent body"))
        assertTrue(checking.contains("<meta http-equiv=\"refresh\" content=\"2\">"))

        val signIn = page.status("en", HandOffProgress.SignIn("Living <room>"))
        assertTrue(signIn.contains("Sign in to Living &lt;room&gt;"))
        assertTrue(signIn.contains("http-equiv=\"refresh\""))

        val connected = page.status("en", HandOffProgress.Connected)
        assertTrue(connected.contains("Done body"))
        assertFalse(connected.contains("http-equiv=\"refresh\""))
    }

    @Test
    fun `the sign-in step offers the server's fields, and holds still so typing isn't lost`() {
        val html = page.status("en", HandOffProgress.SignIn("Living <room>", modes = listOf("Jellyfin", "Local", "ApiKey", "Plex")))

        assertTrue(html.contains("Sign in to Living &lt;room&gt; here"))
        // Hidden until the script has the key; the "finish on your TV" message is what a page without it shows.
        assertTrue(html.contains("<form id=\"signin\" hidden data-mode=\"Jellyfin\">"))
        assertTrue(html.contains("<div id=\"ontv\">"))
        // Only the modes with fields: Plex finishes with a code on the TV.
        assertEquals(3, Regex("<option ").findAll(html).count())
        assertFalse(html.contains("value=\"Plex\""))
        // The script follows the TV; only a browser without it refreshes.
        assertEquals(1, Regex("http-equiv=\"refresh\"").findAll(html).count())
        assertTrue(html.contains("<noscript><meta http-equiv=\"refresh\""))
    }

    @Test
    fun `an address that found no server brings the form back with its own message`() {
        val failed = page.status("en", HandOffProgress.Failed)

        assertTrue(failed.contains("<form method=\"post\">"))
        assertTrue(failed.contains("Not found &lt;here&gt;"))
        assertFalse(failed.contains("Invalid"))
    }

    @Test
    fun `the language follows the browser's priorities and falls back to the first`() {
        assertEquals("es", pickLanguage("es-MX"))
        assertEquals("en", pickLanguage("en-US,es;q=0.9"))
        assertEquals("es", pickLanguage("fr-FR,fr;q=0.9,es;q=0.8,en;q=0.1"))
        assertEquals("en", pickLanguage("de"))
        assertEquals("en", pickLanguage(null))
        assertEquals("en", pickLanguage(";;;q=nonsense"))
    }

    @Test
    fun `form fields decode, and a bad escape reads as no fields`() {
        assertEquals(
            mapOf("address" to "http://seerr.lan:5055/", "x" to ""),
            "address=http%3A%2F%2Fseerr.lan%3A5055%2F&x=".toByteArray().formFields(),
        )
        assertEquals("a b", "address=a+b".toByteArray().formFields()["address"])
        assertEquals(emptyMap<String, String>(), "address=%zz".toByteArray().formFields())
    }

    @Test
    fun `a request that keeps trickling in past its deadline is given up on`() {
        var now = 0L
        val reader =
            HandOffRequestReader(HandOffLimits(requestDeadlineMillis = 10), clock = {
                now += 5
                now
            })
        val input = ByteArrayInputStream("GET / HTTP/1.1\r\nHost: tv\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))

        val failure = runCatching { reader.read(input) }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
    }

    @Test
    fun `a response carries its length and the headers that keep the page private`() {
        val text = HandOffResponse(HttpStatus.Ok, "é").bytes().toString(StandardCharsets.UTF_8)

        assertTrue(text.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(text.contains("Content-Length: 2\r\n"))
        assertTrue(text.contains("Content-Security-Policy: default-src 'none'"))
        assertTrue(text.contains("form-action 'self'"))
        assertTrue(text.endsWith("\r\n\r\né"))
    }

    @Test
    fun `the PIN step posts a 4-digit PIN back to itself, says when one missed, and offers no form once locked`() {
        val html = page.pin(acceptLanguage = null, wrong = false, locked = false)
        assertTrue(html.contains("<form method=\"post\">"))
        assertTrue(html.contains("name=\"pin\""))
        assertTrue(html.contains("pattern=\"[0-9]{4}\""))
        assertTrue(html.contains("Pin title"))
        assertFalse(html.contains("Pin wrong"))

        assertTrue(page.pin(acceptLanguage = null, wrong = true, locked = false).contains("Pin wrong"))

        val locked = page.pin(acceptLanguage = null, wrong = true, locked = true)
        assertTrue(locked.contains("Pin locked"))
        assertFalse(locked.contains("name=\"pin\""))
        // The app is still offered: it asks for the PIN itself, against a code the user opens afresh.
        assertTrue(locked.contains("intent://tv-handoff"))
    }
}
