package com.cooper.binge.companion.seerr.handoff

import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_TV
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val IS_4K = "is4k"
private const val SERVER_ID = "sid"
private const val SERVER_ID_MAX_LENGTH = 64
private val SERVER_ID_SHAPE = Regex("[A-Za-z0-9-]+")
private val MEDIA_TYPES = setOf(SEERR_MEDIA_TYPE_MOVIE, SEERR_MEDIA_TYPE_TV)

/**
 * A title on a server, as a television shows it when it hands "Report an issue" to a phone: the title's own page on the
 * server's web client, `<server>/movie/<id>` or `<server>/tv/<id>`, so a plain camera opens it and the web client reports
 * there. It carries no credential and no token: only which server, which title, and whether the 4K version was meant. Which
 * server is told twice: by address, which differs with the route to it, and by [TitleReportLink.serverId], which does not.
 */
data class TitleReportLink(
    /** The server's address as the code wrote it, which the phone only compares with its own and never contacts. */
    val server: String,
    val mediaType: String,
    val tmdbId: Int,
    val is4k: Boolean,
    /** The server's own name for itself, when the TV's server sends one; null for a code from a server that does not. */
    val serverId: String? = null,
)

internal object TitleReportLinks {
    /** [webUrl], the title's page, marked for the 4K version when [is4k] and with the server's [serverId] when it has one. */
    fun write(
        webUrl: String,
        is4k: Boolean,
        serverId: String? = null,
    ): String {
        val url = webUrl.toHttpUrlOrNull() ?: return webUrl
        val builder = url.newBuilder()
        if (is4k) builder.setQueryParameter(IS_4K, "1")
        serverId?.takeIf { it.isUsableServerId() }?.let { builder.setQueryParameter(SERVER_ID, it) }
        return builder.build().toString()
    }

    /**
     * The title a scanned [code] names, or null for any other text, so a code that is not one is ignored rather than
     * followed. It reads an http or https page whose path ends `/movie/<id>` or `/tv/<id>`, with no user info, and at most
     * the two queries the TV writes. A server id that is not the shape of one is dropped, and the code still reads.
     */
    fun parse(code: String?): TitleReportLink? {
        val url = code?.trim()?.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val query = url.queryParameterNames
        if (query.any { it != IS_4K && it != SERVER_ID }) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val id = segments.lastOrNull()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val mediaType = segments.getOrNull(segments.lastIndex - 1)?.takeIf { it in MEDIA_TYPES } ?: return null
        val server =
            url
                .newBuilder()
                .encodedPath("/" + segments.dropLast(2).joinToString("") { "$it/" })
                .query(null)
                .fragment(null)
                .build()
                .toString()
        return TitleReportLink(
            server = server,
            mediaType = mediaType,
            tmdbId = id,
            is4k = url.queryParameter(IS_4K) == "1",
            serverId = url.queryParameter(SERVER_ID)?.takeIf { it.isUsableServerId() },
        )
    }

    /**
     * Whether [link] names the server this phone is connected to, which is at [connected] and calls itself [connectedId].
     * Two ids that are both known decide it, whatever the addresses say: one server is reached by a LAN address from the
     * TV and a VPN address from the phone. When either side has none, the addresses decide, by the same comparison two
     * spellings of one server go through.
     */
    fun isSameServer(
        link: TitleReportLink,
        connected: String,
        connectedId: String?,
    ): Boolean {
        if (link.serverId != null && connectedId != null) return link.serverId == connectedId
        val theirs = normaliseServerAddress(link.server) ?: return false
        return theirs == normaliseServerAddress(connected)
    }

    private fun String.isUsableServerId() = length <= SERVER_ID_MAX_LENGTH && SERVER_ID_SHAPE.matches(this)
}
