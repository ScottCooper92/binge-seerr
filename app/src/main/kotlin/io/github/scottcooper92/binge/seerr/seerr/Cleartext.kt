package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.CleartextConsent
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * The host, when [this] explicitly asks for cleartext to a PUBLIC one: an `http://` scheme whose host
 * is not loopback, private, LAN or tailnet. The secret would cross the network unencrypted, so setup
 * refuses it unless the user opts in for that host (see `CleartextConsent`). Null for anything else.
 */
fun String.insecurePublicHostOrNull(): String? = trim().toHttpUrlOrNull()?.takeIf { it.isCleartextToPublicHost() }?.host

/** Plain `http` to a host that is not loopback, private, LAN or tailnet: what needs the user's opt-in. */
internal fun HttpUrl.isCleartextToPublicHost(): Boolean = !isHttps && !host.isLocalOrPrivateHost()

private val LOCAL_NAMES = setOf("localhost", "::1")

/** `.localhost` and `.home.arpa` are reserved for this; `.local` is mDNS; `.lan` and `.internal` are what routers hand out. */
private val LOCAL_SUFFIXES = listOf(".localhost", ".local", ".lan", ".home.arpa", ".internal", ".ts.net")

private val LOCAL_ADDRESSES =
    listOf(
        // Loopback, link-local, and the RFC 1918 ranges.
        Regex("""127(\.\d{1,3}){3}"""),
        Regex("""169\.254(\.\d{1,3}){2}"""),
        Regex("""10(\.\d{1,3}){3}"""),
        Regex("""192\.168(\.\d{1,3}){2}"""),
        Regex("""172\.(1[6-9]|2\d|3[01])(\.\d{1,3}){2}"""),
        // The CGNAT range Tailscale assigns from.
        Regex("""100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])(\.\d{1,3}){2}"""),
        // IPv6 unique-local (Tailscale's too) and link-local.
        Regex("""(f[cd]|fe80)[0-9a-f]*:.*"""),
    )

/**
 * Loopback, link-local, the RFC 1918 ranges, single-label names and the suffixes reserved or used
 * for home networks (`.local`, `.lan`, `.home.arpa`, `.internal`), and Tailscale-style addresses —
 * the CGNAT range, `.ts.net` MagicDNS names, IPv6 ULA — where a self-hosted server typically
 * speaks plain HTTP over an already-encrypted link.
 *
 * Judged on the name as typed, never on what it resolves to: a public name that resolves to a LAN
 * address is still public here, because the same name resolves publicly off that LAN.
 *
 * Not the TV-reachability test: `hostLocality` treats the CGNAT and Tailscale ranges as not local,
 * and the two disagree on purpose.
 */
internal fun String.isLocalOrPrivateHost(): Boolean =
    this in LOCAL_NAMES ||
        LOCAL_SUFFIXES.any { endsWith(it) } ||
        LOCAL_ADDRESSES.any { it.matches(this) } ||
        (!contains('.') && !contains(':'))

/** A request this app refused to send: plain HTTP to a public host the user has not opted in for. */
class CleartextRefusedException(
    val host: String,
) : IOException("Refused plain HTTP to $host: it is a public host and the user has not opted in for it")

/**
 * Refuses plain HTTP to a public host that [consent] does not cover.
 *
 * A **network** interceptor, so it judges every hop, including a redirect from an `https` server
 * down to `http`, and runs before a byte of the request is written. Setup already refuses such an
 * address without the opt-in; this is the backstop for every other way a URL reaches a client.
 */
internal class CleartextGuard(
    private val consent: CleartextConsent,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        // runBlocking on an OkHttp thread, and only for the one case that needs the answer.
        if (url.isCleartextToPublicHost() && !runBlocking { consent.allows(url.host) }) throw CleartextRefusedException(url.host)
        return chain.proceed(chain.request())
    }
}
