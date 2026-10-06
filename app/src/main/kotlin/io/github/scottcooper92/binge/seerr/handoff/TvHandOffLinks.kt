package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.ui.DeepLinks
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

private const val TOKEN_LENGTH = 8

/** Lowercase and digits with the look-alikes left out (no 0/o, 1/l/i), because someone may have to type it. */
private const val TOKEN_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
private const val MAX_PORT = 65_535
private const val MAX_OCTET = 255
private const val IPV4_OCTETS = 4

/** [TOKEN_LENGTH] characters of [TOKEN_ALPHABET]. */
private val TOKEN_SHAPE = Regex("[$TOKEN_ALPHABET]{$TOKEN_LENGTH}")

/**
 * A fresh one-time token for one hand-off: about 40 bits from [SecureRandom]. Short enough to type from the
 * address under the code; the listener is LAN-only, answers one connection at a time and lives five minutes.
 */
internal fun newHandOffToken(random: SecureRandom = SecureRandom()): String =
    buildString(TOKEN_LENGTH) { repeat(TOKEN_LENGTH) { append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)]) } }

/**
 * Where a phone sends the address: the TV's LAN IPv4 address, the port it is listening on, and the
 * token that one listener answers to.
 */
data class TvHandOffTarget(
    val host: String,
    val port: Int,
    val token: String,
    /** What seals credentials for this TV, when the link carries it: only a code scanned from the screen does. */
    val key: HandOffKey? = null,
) {
    /** `host:port`, as the link carries it and the confirmation names it. */
    val authority: String get() = "$host:$port"

    /** The one URL the TV answers on; the phone posts the address here. */
    val url: String get() = "http://$authority/a/$token"

    /** Where the TV reports how far along it is, as data. */
    val statusUrl: String get() = "http://$authority/s/$token"

    /** Where a phone posts credentials sealed with [key]. */
    val credentialsUrl: String get() = "http://$authority/c/$token"

    /**
     * Whether this names a television on the user's own network: an IPv4 literal in a private or
     * link-local range, never loopback. The TV only ever writes its own LAN address into a link,
     * so a name, a public address, an overlay address (`100.64.0.0/10`) or this phone itself is a
     * link someone else wrote. This uses [hostLocality], not `isLocalOrPrivateHost`: that one
     * answers the cleartext-consent question and counts the overlay range as local on purpose.
     */
    val isOnLan: Boolean
        get() {
            val octets = host.split('.').map { it.toIntOrNull() }
            val ipv4 = octets.size == IPV4_OCTETS && octets.all { it != null && it in 0..MAX_OCTET }
            return ipv4 && octets.first() != LOOPBACK_FIRST_OCTET && hostLocality(host) == AddressLocality.Local
        }

    private companion object {
        const val LOOPBACK_FIRST_OCTET = 127
    }
}

/**
 * The link from the page a TV serves to this app on the phone, `seerr-companion://tv-handoff?to=<ip:port>&token=<token>`.
 * One place builds and reads it, as [DeepLinks] does for the notification links, so the two ends cannot drift.
 */
internal object TvHandOffLinks {
    const val HOST = "tv-handoff"
    private const val TO = "to"
    private const val TOKEN = "token"
    private const val KEY = "k"

    fun appLink(target: TvHandOffTarget): String =
        "${DeepLinks.SCHEME}://$HOST?$TO=${target.authority}&$TOKEN=${target.token}" +
            (target.key?.let { "&$KEY=${it.encoded()}" } ?: "")

    /**
     * The same link as an `intent:` URL, which is what a browser on the phone can follow into an app:
     * pinned to [packageName] so no other app can answer it, and falling back to [fallbackUrl] when
     * this app is not installed.
     */
    fun intentUrl(
        target: TvHandOffTarget,
        packageName: String,
        fallbackUrl: String,
    ): String =
        "intent://$HOST?$TO=${target.authority}&$TOKEN=${target.token}" +
            "#Intent;scheme=${DeepLinks.SCHEME};package=$packageName;" +
            "S.browser_fallback_url=${URLEncoder.encode(fallbackUrl, StandardCharsets.UTF_8.name())};end"

    /** The target [link] names, or null for anything that is not a well-formed hand-off link. */
    fun parse(link: String?): TvHandOffTarget? {
        val uri =
            try {
                URI(link ?: return null)
            } catch (_: URISyntaxException) {
                return null
            }
        if (!uri.scheme.equals(DeepLinks.SCHEME, ignoreCase = true) || uri.host != HOST) return null
        val query =
            runCatching {
                uri.rawQuery
                    ?.split('&')
                    ?.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8.name()) }
                    .orEmpty()
            }.getOrNull() ?: return null
        val token = query[TOKEN]?.takeIf { TOKEN_SHAPE.matches(it) } ?: return null
        val to = query[TO] ?: return null
        val host = to.substringBeforeLast(':', "").takeIf { it.isNotEmpty() && it.none { c -> c == '/' || c == '@' } } ?: return null
        val port = to.substringAfterLast(':').toIntOrNull()?.takeIf { it in 1..MAX_PORT } ?: return null
        val key = query[KEY]?.let(HandOffKey::parse)
        // A key that is present and malformed is a link someone altered, not one to read without it.
        if (query.containsKey(KEY) && key == null) return null
        return TvHandOffTarget(host = host, port = port, token = token, key = key)
    }
}
