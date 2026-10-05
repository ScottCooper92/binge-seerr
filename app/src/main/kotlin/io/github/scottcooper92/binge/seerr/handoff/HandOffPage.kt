package io.github.scottcooper92.binge.seerr.handoff

import java.util.Locale

/**
 * The languages the page is written in: the locales this app ships. A browser asking for anything
 * else gets the first.
 */
internal val HAND_OFF_PAGE_LANGUAGES = listOf("en", "es")

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
)

/** The pages the TV serves to a phone's browser, in the language the browser asks for. */
internal interface HandOffPage {
    /** The form; [invalid] when the address just posted was not one the TV can use. */
    fun form(
        acceptLanguage: String?,
        invalid: Boolean,
    ): String

    /** The answer to an accepted address. */
    fun sent(acceptLanguage: String?): String

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
) : HandOffPage {
    override fun form(
        acceptLanguage: String?,
        invalid: Boolean,
    ): String {
        val copy = copyFor(pickLanguage(acceptLanguage))
        val error = if (invalid) """<p class="error" role="alert">${copy.invalid.escapeHtml()}</p>""" else ""
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
            <a class="app" href="${appLink.escapeHtml()}">${copy.openApp.escapeHtml()}</a>
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

    override fun sent(acceptLanguage: String?): String {
        val copy = copyFor(pickLanguage(acceptLanguage))
        return page(copy, "<h1>${copy.sentTitle.escapeHtml()}</h1>\n<p>${copy.sentBody.escapeHtml()}</p>")
    }

    private fun page(
        copy: HandOffPageCopy,
        content: String,
    ): String =
        listOf(
            "<!doctype html>",
            "<html lang=\"${copy.language.escapeHtml()}\">",
            "<head>",
            "<meta charset=\"utf-8\">",
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
            "<meta name=\"referrer\" content=\"no-referrer\">",
            "<title>${copy.title.escapeHtml()}</title>",
            "<style>$STYLE</style>",
            "</head>",
            "<body><main>",
            content,
            "</main></body>",
            "</html>",
        ).joinToString("\n")

    private companion object {
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
                ".note a{color:#bcc2ff}"
    }
}
