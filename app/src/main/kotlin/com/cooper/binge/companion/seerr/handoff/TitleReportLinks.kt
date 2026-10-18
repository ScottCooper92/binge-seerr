package com.cooper.binge.companion.seerr.handoff

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val MOVIE = "movie"
private const val TV = "tv"
private const val IS_4K = "is4k"

/**
 * A title on a server, as a television shows it when it hands "Report an issue" to a phone: the title's own page on the
 * server's web client, `<server>/movie/<id>` or `<server>/tv/<id>`, so a plain camera opens it and the web client reports
 * there. It carries no credential and no token: only which server, which title, and whether the 4K version was meant.
 */
data class TitleReportLink(
    /** The server's address as the code wrote it, which the phone only compares with its own and never contacts. */
    val server: String,
    val mediaType: String,
    val tmdbId: Int,
    val is4k: Boolean,
)

internal object TitleReportLinks {
    /** [webUrl], the title's page, marked for the 4K version when [is4k], as the code writes it. */
    fun write(
        webUrl: String,
        is4k: Boolean,
    ): String {
        val url = webUrl.toHttpUrlOrNull() ?: return webUrl
        return if (is4k) {
            url
                .newBuilder()
                .setQueryParameter(IS_4K, "1")
                .build()
                .toString()
        } else {
            url.toString()
        }
    }

    /**
     * The title a scanned [code] names, or null for any other text, so a code that is not one is ignored rather than
     * followed. It reads an http or https page whose path ends `/movie/<id>` or `/tv/<id>`, with no user info, and at most
     * the one query the TV writes.
     */
    fun parse(code: String?): TitleReportLink? {
        val url = code?.trim()?.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val query = url.queryParameterNames
        if (query.any { it != IS_4K }) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val id = segments.lastOrNull()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val mediaType = segments.getOrNull(segments.lastIndex - 1)?.takeIf { it == MOVIE || it == TV } ?: return null
        val server =
            url
                .newBuilder()
                .encodedPath("/" + segments.dropLast(2).joinToString("") { "$it/" })
                .query(null)
                .fragment(null)
                .build()
                .toString()
        return TitleReportLink(server = server, mediaType = mediaType, tmdbId = id, is4k = url.queryParameter(IS_4K) == "1")
    }

    /** Whether [link] names the server [connected], by the same comparison two spellings of one server go through. */
    fun isSameServer(
        link: TitleReportLink,
        connected: String,
    ): Boolean {
        val theirs = normaliseServerAddress(link.server) ?: return false
        return theirs == normaliseServerAddress(connected)
    }
}
