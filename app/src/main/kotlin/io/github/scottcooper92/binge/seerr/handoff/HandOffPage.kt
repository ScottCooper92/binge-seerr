package io.github.scottcooper92.binge.seerr.handoff

import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/**
 * The languages the page is written in: the locales this app ships. A browser asking for anything
 * else gets the first.
 */
internal val HAND_OFF_PAGE_LANGUAGES = listOf("en", "es")

/** [PAGE_SCRIPT]'s content-security-policy source: the page may run this script and no other. */
internal val PAGE_SCRIPT_HASH: String =
    "sha256-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(PAGE_SCRIPT.toByteArray(Charsets.UTF_8)))

/** The release app's Play listing — the build a phone should get, whichever build the TV runs. */
internal const val HAND_OFF_STORE_URL = "https://play.google.com/store/apps/details?id=io.github.scottcooper92.binge.seerr"

/** The page's words in one language, read from the app's own strings so they are translated like any other screen. */
internal data class HandOffPageCopy(
    val language: String,
    val title: String,
    val body: String,
    val field: String,
    val placeholder: String,
    val send: String,
    val invalid: String,
    val appBody: String,
    val openApp: String,
    /** "You can install Seerr from the Google Play Store": the sentence, with [storeName] inside it as the link. */
    val install: String,
    val storeName: String,
    val sentTitle: String,
    val sentBody: String,
    val failed: String,
    val signInTitle: String,
    /** Names the server the TV found, in its own sentence. */
    val signInBody: (server: String) -> String,
    val connectedTitle: String,
    val connectedBody: String,
    /** The sign-in form the page shows on the TV's sign-in step (#772), naming the server. */
    val signInFormTitle: (server: String) -> String,
    val signInFormBody: String,
    val modeField: String,
    /** A sign-in mode's name as the page offers it, by the mode's name on the wire. */
    val modeLabel: (mode: String) -> String,
    val username: String,
    val email: String,
    val password: String,
    val apiKey: String,
    val signIn: String,
    val signingIn: String,
    val rejected: String,
)

/** The pages the TV serves to a phone's browser, in the language the browser asks for. */
internal interface HandOffPage {
    /** The form; [invalid] when the address just posted was not one the TV can use. */
    fun form(
        acceptLanguage: String?,
        invalid: Boolean,
    ): String

    /**
     * The page for wherever the TV has got to. Every state but the last asks the browser to read it again
     * in a moment, so the page follows the TV with no script in it.
     */
    fun status(
        acceptLanguage: String?,
        progress: HandOffProgress,
    ): String

    /** The bare page for a refused request, which says nothing about what is listening. */
    fun refused(): String = "<!doctype html><title>-</title>"
}

/**
 * The best of [supported] for an `Accept-Language` header, by the header's own priorities, falling
 * back from a regional tag (`es-MX`) to its language (`es`). The first supported language when the
 * header is absent, matches nothing or does not parse.
 */
internal fun pickLanguage(
    acceptLanguage: String?,
    supported: List<String> = HAND_OFF_PAGE_LANGUAGES,
): String {
    val ranges = acceptLanguage?.let { runCatching { Locale.LanguageRange.parse(it) }.getOrNull() }.orEmpty()
    return Locale.lookupTag(ranges, supported) ?: supported.first()
}

/**
 * One small self-contained page: inline styles, no scripts, nothing fetched from anywhere. The
 * form posts back to the URL it was served from, and the app link opens this app on a phone that
 * has it, so the connected server's address can be sent without typing. A note under it points a phone
 * without the app at its store listing.
 */
internal class HandOffPageTemplate(
    private val copyFor: (language: String) -> HandOffPageCopy,
    /** The `intent:` link into this app; see [TvHandOffLinks.intentUrl]. */
    private val appLink: String,
    /** This app's store listing, for a phone that doesn't have it yet. */
    private val storeLink: String = HAND_OFF_STORE_URL,
    /** The listener's token, for the page script's requests back to it. */
    private val token: String = "",
) : HandOffPage {
    override fun form(
        acceptLanguage: String?,
        invalid: Boolean,
    ): String = form(acceptLanguage, invalid, problem = null)

    private fun form(
        acceptLanguage: String?,
        invalid: Boolean,
        problem: String?,
    ): String {
        val copy = copyFor(pickLanguage(acceptLanguage))
        val message = problem ?: copy.invalid.takeIf { invalid }
        val error = if (message != null) """<p class="error" role="alert">${message.escapeHtml()}</p>""" else ""
        return page(
            copy,
            """
            <h1>${copy.title.escapeHtml()}</h1>
            <p>${copy.body.escapeHtml()}</p>
            <form method="post">
            <label for="address">${copy.field.escapeHtml()}</label>
            <input id="address" name="address" type="text" inputmode="url" autocapitalize="none" autocorrect="off"
             spellcheck="false" autocomplete="url" required placeholder="${copy.placeholder.escapeHtml()}">
            $error
            <button type="submit">${copy.send.escapeHtml()}</button>
            </form>
            <hr>
            <p>${copy.appBody.escapeHtml()}</p>
            <a class="app" id="app" href="${appLink.escapeHtml()}">${copy.openApp.escapeHtml()}</a>
            <p class="note">${installLine(copy)}</p>
            """.trimIndent(),
        )
    }

    /** The install sentence with the store's name as the link; the whole sentence as the link if a translation drops the name. */
    private fun installLine(copy: HandOffPageCopy): String {
        val link = """<a href="${storeLink.escapeHtml()}">${copy.storeName.escapeHtml()}</a>"""
        val at = copy.install.indexOf(copy.storeName)
        return if (copy.storeName.isEmpty() || at < 0) {
            """<a href="${storeLink.escapeHtml()}">${copy.install.escapeHtml()}</a>"""
        } else {
            copy.install.substring(0, at).escapeHtml() + link + copy.install.substring(at + copy.storeName.length).escapeHtml()
        }
    }

    override fun status(
        acceptLanguage: String?,
        progress: HandOffProgress,
    ): String {
        val copy = copyFor(pickLanguage(acceptLanguage))
        return when (progress) {
            HandOffProgress.Waiting -> form(acceptLanguage, invalid = false)
            HandOffProgress.Failed -> form(acceptLanguage, invalid = false, problem = copy.failed)
            HandOffProgress.Checking -> message(copy, copy.sentTitle, copy.sentBody, following = true)
            is HandOffProgress.SignIn -> signIn(copy, progress)
            HandOffProgress.Connected -> message(copy, copy.connectedTitle, copy.connectedBody, following = false)
        }
    }

    /**
     * The TV's sign-in step: the server's own fields, which the page script seals for the TV. Until the script has the
     * key, which only a scanned code carries, the page says to finish on the TV, and that is what a page without the
     * script shows. It does not refresh itself, so nothing typed is lost; the script follows the TV instead.
     */
    private fun signIn(
        copy: HandOffPageCopy,
        progress: HandOffProgress.SignIn,
    ): String {
        val modes = progress.modes.filter { mode -> HandOffSignInModes.any { it.name == mode } }
        if (modes.isEmpty()) return message(copy, copy.signInTitle, copy.signInBody(progress.server), following = true)
        val chooser =
            if (modes.size > 1) {
                val options =
                    modes.joinToString(
                        "",
                    ) { """<option value="${it.escapeHtml()}">${copy.modeLabel(it).escapeHtml()}</option>""" }
                """<label for="mode">${copy.modeField.escapeHtml()}</label><select id="mode">$options</select>"""
            } else {
                ""
            }
        val content =
            """
            <div id="ontv"><h1>${copy.signInTitle.escapeHtml()}</h1><p>${copy.signInBody(progress.server).escapeHtml()}</p></div>
            <form id="signin" hidden data-mode="${modes.first().escapeHtml()}">
            <h1>${copy.signInFormTitle(progress.server).escapeHtml()}</h1>
            <p>${copy.signInFormBody.escapeHtml()}</p>
            $chooser
            <div data-for="Jellyfin Emby"><label for="username">${copy.username.escapeHtml()}</label>
            <input id="username" autocomplete="username" autocapitalize="none" autocorrect="off" spellcheck="false"></div>
            <div data-for="Local"><label for="email">${copy.email.escapeHtml()}</label>
            <input id="email" type="email" autocomplete="username" autocapitalize="none"></div>
            <div data-for="Jellyfin Emby Local"><label for="password">${copy.password.escapeHtml()}</label>
            <input id="password" type="password" autocomplete="current-password"></div>
            <div data-for="ApiKey"><label for="apikey">${copy.apiKey.escapeHtml()}</label>
            <input id="apikey" autocomplete="off" autocapitalize="none" spellcheck="false"></div>
            <p class="error" id="err" role="alert" hidden>${copy.rejected.escapeHtml()}</p>
            <button type="submit" id="go" data-idle="${copy.signIn.escapeHtml()}" data-busy="${copy.signingIn.escapeHtml()}">${copy.signIn.escapeHtml()}</button>
            </form>
            """.trimIndent()
        return page(copy, content, refresh = false, refreshWithoutScript = true)
    }

    private fun message(
        copy: HandOffPageCopy,
        title: String,
        body: String,
        following: Boolean,
    ): String = page(copy, "<h1>${title.escapeHtml()}</h1>\n<p>${body.escapeHtml()}</p>", refresh = following)

    private fun page(
        copy: HandOffPageCopy,
        content: String,
        refresh: Boolean = false,
        refreshWithoutScript: Boolean = false,
    ): String =
        listOf(
            "<!doctype html>",
            "<html lang=\"${copy.language.escapeHtml()}\" data-token=\"${token.escapeHtml()}\">",
            "<head>",
            "<meta charset=\"utf-8\">",
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
            "<meta name=\"referrer\" content=\"no-referrer\">",
            if (refresh) "<meta http-equiv=\"refresh\" content=\"$REFRESH_SECONDS\">" else "",
            if (refreshWithoutScript) "<noscript><meta http-equiv=\"refresh\" content=\"$REFRESH_SECONDS\"></noscript>" else "",
            "<title>${copy.title.escapeHtml()}</title>",
            "<style>$STYLE</style>",
            "</head>",
            "<body><main>",
            content,
            "</main>",
            "<script>$PAGE_SCRIPT</script>",
            "</body>",
            "</html>",
        ).joinToString("\n")

    private companion object {
        /** How often a page that follows the TV asks for itself again. */
        const val REFRESH_SECONDS = 2

        /** The app's dark surface and indigo accent, close enough that the page reads as the app's own. */
        const val STYLE =
            "body{margin:0;background:#121218;color:#e6e1f0;font:16px/1.5 system-ui,sans-serif}" +
                "main{max-width:32rem;margin:0 auto;padding:1.5rem}" +
                "h1{font-size:1.5rem;margin:0 0 .5rem}" +
                "label{display:block;margin:1rem 0 .25rem;font-weight:600}" +
                "input{box-sizing:border-box;width:100%;padding:.75rem;font:inherit;font-family:monospace;" +
                "border:1px solid #8f8a9e;border-radius:.5rem;background:#1d1c24;color:inherit}" +
                "button,a.app{display:block;box-sizing:border-box;width:100%;margin-top:1rem;padding:.75rem;" +
                "border:0;border-radius:1.5rem;font:inherit;font-weight:600;text-align:center;text-decoration:none}" +
                "button{background:#bcc2ff;color:#1a2178}" +
                "a.app{border:1px solid #8f8a9e;color:#bcc2ff}" +
                "hr{border:0;border-top:1px solid #2b2a33;margin:1.5rem 0}" +
                ".error{color:#ffb4ab}" +
                ".note{margin:.75rem 0 0;color:#a6a1b4;font-size:.875rem;text-align:center}" +
                ".note a{color:#bcc2ff}" +
                "select{box-sizing:border-box;width:100%;padding:.75rem;font:inherit;border:1px solid #8f8a9e;" +
                "border-radius:.5rem;background:#1d1c24;color:inherit}" +
                "button:disabled{opacity:.6}" +
                "[hidden]{display:none!important}"
    }
}
